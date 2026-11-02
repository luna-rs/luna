package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.trading.RuneRunningTrade
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.social.BotConversations
import api.bot.social.SocialBotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.Zone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.RuneRunningCoordinator
import engine.bot.coordinator.RuneRunningCoordinator.BATCH
import engine.bot.coordinator.RuneRunningCoordinator.COINS
import engine.bot.coordinator.RuneRunningCoordinator.NATURE_FARE
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Crafts runner deliveries using an account's real essence and ordinary altar interactions.
 * Only skiller accounts can host. Exchanges happen outside the ruins so a fresh runner needs no access item;
 * this crafter remains at the altar complex, enters to craft each load, then returns to the waiting runners.
 *
 * @author lare96
 */
class RuneCrafterBotScript(bot: Bot, data: CrafterData) : BotScript(bot), SocialBotScript {

    val altar = data.altar
    private val essence = RuneRunningCoordinator.essence(altar)
    private val deadline = data.deadline
    private val rune = CraftableRune.ALTAR_TO_RUNE[altar]
    private var prepared = false
    private var registered = false
    private var lastDeliveryAt = System.currentTimeMillis()
    private var delivery: RuneRunningCoordinator.Delivery? = null
    private var guardsInstalled = false
    private var previousCombatDisabled = false

    /**
     * Starts a finite session without saving live reservations or making new items.
     */
    constructor(bot: Bot, altar: Altar, duration: Duration) : this(bot, CrafterData().also {
        it.altar = altar
        it.deadline = System.currentTimeMillis() + duration.inWholeMilliseconds
    })

    override val allowsCombatResponse = false
    override val actionSafety = ActionSafety.COMBAT

    override fun allowsLoot(item: GroundItem): Boolean = false

    override fun canSocialize(): Boolean = delivery?.dialogue?.let {
        (!ending() || it.acknowledging) && it.canSocialize(bot)
    } == true

    private fun eligible(): Boolean = bot.preferences.isSkiller && altar in RuneRunningCoordinator.ALTARS &&
        rune != null && bot.runecrafting.level >= rune.level && bot.isAlive

    private fun ending(): Boolean = System.currentTimeMillis() >= deadline || !eligible()

    /**
     * Live stock is advertised only while this session can pay for another exact exchange.
     */
    fun canSupply(): Boolean {
        val note = itemDef(essence).notedId.orElse(-1)
        return !ending() && prepared && note >= 0 && bot.inventory.computeAmountForId(note) >= BATCH &&
            bot.inventory.computeAmountForId(rune!!.id) > 0 &&
            (altar != Altar.NATURE || bot.inventory.computeAmountForId(COINS) >= NATURE_FARE)
    }

    override suspend fun init(resumed: Boolean): Boolean {
        if (ending()) return true
        if (!guardsInstalled) {
            previousCombatDisabled = bot.combat.isDisabled
            bot.combat.isDisabled = true
            guardsInstalled = true
        }
        bot.combat.target = null
        if (bot.combat.inCombat()) {
            RuneRunningCoordinator.unregister(bot)
            flee()
            bankRemainder()
            return true
        }
        prepared = false
        registered = false
        lastDeliveryAt = System.currentTimeMillis()
        return false
    }

    override suspend fun run(): Boolean {
        bot.combat.target = null
        if (bot.combat.inCombat()) {
            RuneRunningCoordinator.unregister(bot)
            flee()
            bankRemainder()
            return true
        }
        if (ending() || !isActionSafe(ActionSafety.NON_COMBAT)) {
            RuneRunningCoordinator.unregister(bot)
            bankRemainder()
            return true
        }
        if (!prepared) {
            if (!pack() || !craftLoad()) {
                bankRemainder()
                return true
            }
            prepared = true
        }
        if (bot.inventory.contains(essence)) {
            if (!craftLoad()) return true
        }
        if (!canSupply()) {
            RuneRunningCoordinator.unregister(bot)
            bankRemainder()
            return true
        }
        if (!registered) {
            if (!RuneRunningCoordinator.register(this)) return true
            registered = true
        }
        val agreement = RuneRunningCoordinator.pending(bot)
        if (agreement == null) {
            val idle = if (altar == Altar.NATURE) 10.minutes else 5.minutes
            if (System.currentTimeMillis() - lastDeliveryAt >= idle.inWholeMilliseconds) {
                RuneRunningCoordinator.unregister(bot)
                bankRemainder()
                return true
            }
            delay(2.seconds)
            return false
        }
        delivery = agreement
        RuneRunningCoordinator.start(agreement, bot)
        val success = try {
            val discussed = agreement.dialogue.beforeTrade(bot)
            val exchanged = discussed && RuneRunningTrade.exchange(bot, agreement.runner,
                agreement.crafterGives, agreement.runnerGives) {
                    !ending() && RuneRunningCoordinator.valid(agreement)
            }
            agreement.dialogue.afterTrade(bot, exchanged, leaving = ending())
            exchanged
        } finally {
            agreement.dialogue.close()
            RuneRunningCoordinator.complete(agreement, bot)
            delivery = null
        }
        if (success) lastDeliveryAt = System.currentTimeMillis()
        else delay(2.seconds)
        return false
    }

    /**
     * Withdraws one loose crafting load plus a finite stock of noted replacements. Native bank mode owns noting.
     * Nature hosts pack their own outbound fare and a real sixty-coin reimbursement for every noted load.
     */
    private suspend fun pack(): Boolean = withTimeoutOrNull(3.minutes) {
        val previous = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        try {
            if (!eligible() || !isActionSafe(ActionSafety.NON_COMBAT) || !openBank() ||
                !handler.banking.depositInventory() || !handler.equipment.unequipAll() ||
                !handler.banking.depositInventory()) return@withTimeoutOrNull false
            var loads = minOf(40, (bot.bank.computeAmountForId(essence) - BATCH) / BATCH)
            if (altar == Altar.NATURE) loads = minOf(loads,
                (bot.bank.computeAmountForId(COINS) - NATURE_FARE).coerceAtLeast(0) / NATURE_FARE)
            if (loads < 1 || bot.bank.computeAmountForId(altar.talisman) < 1) return@withTimeoutOrNull false
            handler.banking.clickBankingMode(false)
            if (!handler.banking.withdraw(Item(altar.talisman)) ||
                !handler.banking.withdraw(Item(essence, BATCH)) ||
                bot.inventory.computeAmountForId(essence) != BATCH || !bot.inventory.contains(altar.talisman))
                return@withTimeoutOrNull false
            if (altar == Altar.NATURE) {
                val amount = loads * NATURE_FARE + NATURE_FARE
                if (!handler.banking.withdraw(Item(COINS, amount)) ||
                    bot.inventory.computeAmountForId(COINS) != amount) return@withTimeoutOrNull false
            }
            val note = itemDef(essence).notedId.orElse(-1)
            if (note < 0) return@withTimeoutOrNull false
            handler.banking.clickBankingMode(true)
            val amount = loads * BATCH
            handler.banking.withdraw(Item(essence, amount)) &&
                bot.inventory.computeAmountForId(note) == amount && isActionSafe(ActionSafety.NON_COMBAT)
        } finally {
            if (bot.bank.isOpen) handler.banking.clickBankingMode(previous != 0)
            bot.overlays.closeWindows()
        }
    } == true

    /**
     * Enters through the existing talisman crossing, clicks the native altar, verifies consumption and exits.
     */
    private suspend fun craftLoad(): Boolean = withTimeoutOrNull(3.minutes) {
        if (ending() || !isActionSafe(ActionSafety.NON_COMBAT) ||
            bot.inventory.computeAmountForId(essence) != BATCH || !bot.inventory.contains(altar.talisman))
            return@withTimeoutOrNull false
        if (!handler.webWalkTo(altar.zone, radius = 3, bank = BankAccess.NONE, fallback = false) ||
            !isActionSafe(ActionSafety.NON_COMBAT)) return@withTimeoutOrNull false
        val altarObject = world.locator.findObjects(altar.zone.inside, altar.zone.area.tileRadius) {
            it.id == rune!!.insideAltarObject
        }.firstOrNull() ?: return@withTimeoutOrNull false
        val before = bot.inventory.computeAmountForId(rune!!.id)
        if (!handler.interactions.interact(1, altarObject) { eligible() && isActionSafe(ActionSafety.NON_COMBAT) } ||
            !waitFor(10.seconds) {
                bot.inventory.computeAmountForId(essence) == 0 && bot.inventory.computeAmountForId(rune.id) > before
            }) return@withTimeoutOrNull false
        handler.webWalkTo(altar.exit, radius = 2, bank = BankAccess.NONE, fallback = false) &&
            isActionSafe(ActionSafety.NON_COMBAT)
    } == true

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

    /**
     * Repeats native escape movement while the combat timer settles, without starting any attacks.
     */
    private suspend fun flee() = withTimeoutOrNull(30.seconds) {
        while (bot.isAlive && bot.combat.inCombat()) {
            handler.combat.fleeCombat()
            delay(1.seconds)
        }
    }

    /**
     * Keeps nature stock on the known Draynor bank route until Shilo's approach is independently supported.
     */
    private suspend fun openBank(): Boolean {
        if (altar == Altar.NATURE && !handler.webWalkTo(Zone.DRAYNOR, bank = BankAccess.NONE, fallback = false))
            return false
        return handler.banking.travelToBankOpen()
    }

    override fun paused() {
        delivery?.dialogue?.close()
        BotConversations.cancel(bot)
        RuneRunningCoordinator.unregister(bot)
        registered = false
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() {
        delivery?.dialogue?.close()
        BotConversations.cancel(bot)
        delivery?.let { RuneRunningCoordinator.complete(it, bot) }
        delivery = null
        RuneRunningCoordinator.unregister(bot)
        if (!isPaused() && guardsInstalled) {
            bot.combat.isDisabled = previousCombatDisabled
            guardsInstalled = false
        }
    }

    override fun snapshot(): BotScriptData = CrafterData().also {
        it.altar = altar
        it.deadline = deadline
    }

    /**
     * Stores the selected supported route and absolute budget. All supplies remain ordinary player-owned items.
     *
     * @author lare96
     */
    class CrafterData : BotScriptData() {
        var altar = Altar.AIR
        var deadline = 0L

        override fun load(data: JsonObject) {
            altar = Altar.valueOf(data.get("altar").asString)
            deadline = data.get("deadline")?.asLong ?: 0L
        }

        override fun save(data: JsonObject) {
            data.addProperty("altar", altar.name)
            data.addProperty("deadline", deadline)
        }
    }
}
