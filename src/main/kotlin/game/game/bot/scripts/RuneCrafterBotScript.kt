package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler.Companion.WEB_WALK_ATTEMPTS
import api.bot.action.trading.RuneRunningTrade
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.social.SocialBotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.Zone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.RuneRunningCoordinator
import engine.bot.coordinator.RuneRunningCoordinator.BATCH
import engine.bot.coordinator.RuneRunningCoordinator.COINS
import engine.bot.coordinator.RuneRunningCoordinator.HostTerms
import engine.bot.coordinator.RuneRunningCoordinator.Route
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Holds a financed session at one altar. The crafter pays for delivery at the ruins, then
 * enters through the native access item and keeps the output and experience from the normal altar action.
 *
 * @author lare96
 */
class RuneCrafterBotScript(bot: Bot, data: CrafterData) : BotScript(bot), SocialBotScript {

    val altar = data.altar
    val essenceId = data.essenceId
    private val route = Route(altar, essenceId)
    private val deadline = data.deadline
    private var previousCombatDisabled: Boolean? = null
    private var delivery: RuneRunningCoordinator.Delivery? = null
    private var idleSince = 0L

    constructor(bot: Bot, altar: Altar, duration: Duration, essenceId: Int = RuneRunningCoordinator.essence(altar)) :
        this(bot, CrafterData().also {
            it.altar = altar
            it.essenceId = essenceId
            it.deadline = System.currentTimeMillis() + duration.inWholeMilliseconds
        })

    override val allowsCombatResponse = false
    override val actionSafety = ActionSafety.COMBAT

    override fun allowsLoot(item: GroundItem): Boolean = false

    override fun canSocialize(): Boolean = delivery?.dialogue?.canSocialize(bot) == true

    private fun ending(): Boolean = System.currentTimeMillis() >= deadline ||
        !RuneRunningCoordinator.crafterEligible(bot, route)

    override suspend fun init(resumed: Boolean): Boolean {
        if (resumed || ending()) return true
        val offer = RuneRunningCoordinator.hostTerms(this) ?: return true
        previousCombatDisabled = bot.combat.isExplicitlyDisabled
        bot.combat.isDisabled = true
        bot.combat.target = null
        if (!pack(offer) || !walkToMeeting() || !RuneRunningCoordinator.register(this)) {
            bankRemainder()
            return true
        }
        idleSince = System.currentTimeMillis()
        return false
    }

    override suspend fun run(): Boolean {
        bot.combat.target = null
        if (bot.combat.inCombat()) {
            RuneRunningCoordinator.unregister(this)
            withTimeoutOrNull(30.seconds) {
                while (bot.isAlive && bot.combat.inCombat()) {
                    handler.combat.fleeCombat()
                    delay(1.seconds)
                }
            }
            bankRemainder()
            return true
        }
        if (ending() || !RuneRunningCoordinator.hasFunds(this)) {
            RuneRunningCoordinator.unregister(this)
            bankRemainder()
            return true
        }
        val agreement = RuneRunningCoordinator.pending(this)
        if (agreement == null) {
            if (RuneRunningCoordinator.serving(this)) idleSince = System.currentTimeMillis()
            if (System.currentTimeMillis() - idleSince > 45_000L) {
                RuneRunningCoordinator.unregister(this)
                bankRemainder()
                return true
            }
            delay(1.seconds)
            return false
        }
        delivery = agreement
        try {
            if (agreement.dialogue.beforeTrade(bot)) {
                RuneRunningTrade.exchange(bot, agreement.runner, agreement.crafterGives, agreement.runnerGives,
                    onPaid = { RuneRunningCoordinator.paid(agreement) }) {
                    !ending() && RuneRunningCoordinator.valid(agreement)
                }
                agreement.dialogue.afterTrade(bot)
            }
        } finally {
            RuneRunningCoordinator.finishDelivery(agreement, bot)
            delivery = null
        }
        if (agreement.paid) {
            val output = craftLoad()
            if (output <= 0) {
                RuneRunningCoordinator.unregister(this)
                bankRemainder()
                return true
            }
            RuneRunningCoordinator.crafted(agreement, output)
            if (RuneRunningCoordinator.hasFunds(this) && (!walkToMeeting() || !RuneRunningCoordinator.register(this))) {
                RuneRunningCoordinator.unregister(this)
                bankRemainder()
                return true
            }
            idleSince = System.currentTimeMillis()
        }
        return false
    }

    private suspend fun pack(offer: HostTerms): Boolean = withTimeoutOrNull(RuneRunningCoordinator.tripTimeout(altar)) {
        val previous = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        val entryItem = if (offer.tiara) altar.tiara else altar.talisman
        try {
            if (ending() || !openBank() || !handler.banking.depositInventory() ||
                !handler.equipment.unequipAll() || !handler.banking.depositInventory())
                return@withTimeoutOrNull false
            handler.banking.clickBankingMode(false)
            if (!handler.banking.withdraw(Item(entryItem))) return@withTimeoutOrNull false
            val gold = offer.loads * offer.fee + RuneRunningCoordinator.fare(altar)
            if (!handler.banking.withdraw(Item(COINS, gold)) || bot.inventory.computeAmountForId(COINS) != gold)
                return@withTimeoutOrNull false
            val note = itemDef(essenceId).notedId.orElse(-1)
            if (note < 0) return@withTimeoutOrNull false
            handler.banking.clickBankingMode(true)
            val amount = offer.loads * BATCH
            if (!handler.banking.withdraw(Item(essenceId, amount)) ||
                bot.inventory.computeAmountForId(note) != amount) return@withTimeoutOrNull false
        } finally {
            if (bot.bank.isOpen) handler.banking.clickBankingMode(previous != 0)
            bot.overlays.closeWindows()
        }
        (!offer.tiara || handler.equipment.equip(entryItem)) &&
            isActionSafe(ActionSafety.NON_COMBAT)
    } == true

    private suspend fun walkToMeeting(): Boolean = withTimeoutOrNull(RuneRunningCoordinator.tripTimeout(altar)) {
        handler.webWalkTo(altar.exit, radius = 2, attempts = WEB_WALK_ATTEMPTS,
            bank = BankAccess.NONE, fallback = false) &&
            RuneRunningCoordinator.atMeeting(bot, route)
    } == true

    private suspend fun craftLoad(): Int = withTimeoutOrNull(RuneRunningCoordinator.tripTimeout(altar)) {
        if (ending() || bot.inventory.computeAmountForId(essenceId) != BATCH ||
            (!bot.inventory.contains(altar.talisman) && bot.equipment[Equipment.HEAD]?.id != altar.tiara))
            return@withTimeoutOrNull 0
        if (!handler.webWalkTo(altar.zone, radius = 3, attempts = WEB_WALK_ATTEMPTS,
                bank = BankAccess.NONE, fallback = false) || ending())
            return@withTimeoutOrNull 0
        val rune = route.rune
        val altarObject = world.locator.findObjects(altar.zone.inside, altar.zone.area.tileRadius) {
            it.id == rune.insideAltarObject
        }.firstOrNull() ?: return@withTimeoutOrNull 0
        val before = bot.inventory.computeAmountForId(rune.id)
        if (!handler.interactions.interactLocally(1, altarObject) { !ending() } ||
            !waitFor(10.seconds) {
                bot.inventory.computeAmountForId(essenceId) == 0 && bot.inventory.computeAmountForId(rune.id) > before
            }) return@withTimeoutOrNull 0
        bot.inventory.computeAmountForId(rune.id) - before
    } ?: 0

    private suspend fun bankRemainder() {
        if (!isActionSafe(ActionSafety.NON_COMBAT)) return
        withTimeoutOrNull(2.minutes) {
            try {
                if (openBank()) handler.banking.depositInventory()
            } finally {
                bot.overlays.closeWindows()
            }
        }
    }

    private suspend fun openBank(): Boolean {
        if (bot.bank.isOpen) return true
        if (altar == Altar.NATURE && !handler.webWalkTo(Zone.DRAYNOR, bank = BankAccess.NONE, fallback = false))
            return false
        val bank = handler.webWalkToNearestBank(attempts = WEB_WALK_ATTEMPTS, fallback = false) ?: return false
        return handler.banking.open(bank)
    }

    override fun paused() {
        RuneRunningCoordinator.unregister(this)
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() {
        RuneRunningCoordinator.unregister(this)
        previousCombatDisabled?.let { bot.combat.isDisabled = it }
        previousCombatDisabled = null
    }

    override fun snapshot(): BotScriptData = CrafterData().also {
        it.altar = altar
        it.essenceId = essenceId
        it.deadline = deadline
    }

    /**
     * Stores the selected route and absolute session deadline.
     *
     * @author lare96
     */
    class CrafterData : BotScriptData() {
        var altar = Altar.AIR
        var essenceId = 1436
        var deadline = 0L

        override fun load(data: JsonObject) {
            altar = Altar.valueOf(data.get("altar").asString)
            essenceId = data.get("essenceId")?.asInt ?: RuneRunningCoordinator.legacyEssence(altar)
            deadline = data.get("deadline")?.asLong ?: 0L
        }

        override fun save(data: JsonObject) {
            data.addProperty("altar", altar.name)
            data.addProperty("essenceId", essenceId)
            data.addProperty("deadline", deadline)
        }
    }
}
