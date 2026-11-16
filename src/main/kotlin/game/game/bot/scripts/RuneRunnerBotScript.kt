package game.bot.scripts

import api.bot.Suspendable.delay
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
import engine.bot.coordinator.MerchantingCoordinator
import engine.bot.coordinator.RuneRunningCoordinator.BATCH
import engine.bot.coordinator.RuneRunningCoordinator.COINS
import engine.bot.coordinator.RuneRunningCoordinator.NATURE_FARE
import game.skill.runecrafting.enterAltar.Altar
import game.obj.entrances.Zanaris
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Carries real essence between a bank and a funded skiller's outside-ruins exchange point.
 * Runners never craft or enter an altar. Their first load can be funded once through a native noted-essence gift;
 * subsequent deliveries receive the actual crafted rune output and a replacement load of noted essence.
 *
 * @author lare96
 */
class RuneRunnerBotScript(bot: Bot, data: RunnerData) : BotScript(bot), SocialBotScript {

    val altar = data.altar
    val essenceId = data.essenceId
    private val essence get() = essenceId
    val introductory = data.introductory
    private val deadline = data.deadline
    private var seedUsed = data.seedUsed
    private var introducedHost = data.introducedHost
    private var prepared = false
    private var failures = 0
    private var delivery: RuneRunningCoordinator.Delivery? = null
    private var guardsInstalled = false
    private var previousCombatDisabled = false
    private var failed = false

    /**
     * Creates a bounded job; offline time cannot renew its absolute expiry.
     */
    constructor(bot: Bot, altar: Altar, introductory: Boolean, duration: Duration,
                essenceId: Int = RuneRunningCoordinator.essence(altar)) : this(bot, RunnerData().also {
        it.altar = altar
        it.essenceId = essenceId
        it.introductory = introductory
        it.deadline = System.currentTimeMillis() + duration.inWholeMilliseconds
    })

    override val allowsCombatResponse = false
    override val actionSafety = ActionSafety.COMBAT

    override fun allowsLoot(item: GroundItem): Boolean = false

    override fun canSocialize(): Boolean = delivery?.dialogue?.let {
        (!ending() || it.acknowledging) && it.canSocialize(bot)
    } == true

    override suspend fun init(resumed: Boolean): Boolean {
        if (ending() || !RuneRunningCoordinator.validEssence(altar, essence) ||
            RuneRunningCoordinator.reserve(bot, altar, essence) == null) return true
        if (!guardsInstalled) {
            previousCombatDisabled = bot.combat.isDisabled
            bot.combat.isDisabled = true
            guardsInstalled = true
        }
        bot.combat.target = null
        prepared = bot.inventory.computeAmountForId(essence) == BATCH && bot.position.isWithinDistance(altar.exit, 3)
        return false
    }

    private fun ending(): Boolean = System.currentTimeMillis() >= deadline || bot.combatLevel >= 50 ||
        introductory && !bot.preferences.runeRunning.isIntroductory

    override suspend fun run(): Boolean {
        bot.combat.target = null
        if (bot.combat.inCombat()) {
            RuneRunningCoordinator.release(bot)
            withTimeoutOrNull(30.seconds) {
                while (bot.isAlive && bot.combat.inCombat()) {
                    handler.combat.fleeCombat()
                    delay(1.seconds)
                }
            }
            bankEarnings()
            return true
        }
        if (ending() || !RuneRunningCoordinator.runnerEligible(bot) || RuneRunningCoordinator.host(bot) == null) {
            bankEarnings()
            return true
        }
        if (!prepared) {
            if (!pack()) return failedAttempt()
            prepared = true
        }
        val reached = withTimeoutOrNull(RuneRunningCoordinator.tripTimeout(altar)) {
            handler.webWalkTo(altar.exit, radius = 2, bank = BankAccess.NONE, fallback = false)
        } == true
        if (!reached || !isActionSafe(ActionSafety.NON_COMBAT)) return failedAttempt()
        val needsSeed = bot.inventory.computeAmountForId(essence) < BATCH
        if (needsSeed && seedUsed) return true
        // A busy host may be serving another reserved runner. Bound waiting independently of trade-screen timeouts.
        val agreement = withTimeoutOrNull(2.minutes) {
            var next: RuneRunningCoordinator.Delivery? = null
            while (!ending() && RuneRunningCoordinator.host(bot) != null &&
                isActionSafe(ActionSafety.NON_COMBAT) && next == null) {
                next = RuneRunningCoordinator.request(bot, needsSeed)
                if (next == null) delay(2.seconds)
            }
            next
        } ?: return failedAttempt()
        delivery = agreement
        RuneRunningCoordinator.start(agreement, bot)
        val success = try {
            val discussed = agreement.dialogue.beforeTrade(bot, introducedHost != agreement.crafter.username)
            if (agreement.dialogue.introductionDelivered) introducedHost = agreement.crafter.username
            // Consume the gift allowance only after the spoken terms, before its native trade can complete.
            if (discussed && agreement.seed) seedUsed = true
            val exchanged = discussed && RuneRunningTrade.exchange(bot, agreement.crafter,
                agreement.runnerGives, agreement.crafterGives) {
                    !ending() && RuneRunningCoordinator.valid(agreement)
            }
            agreement.dialogue.afterTrade(bot, exchanged, leaving = ending())
            exchanged
        } finally {
            agreement.dialogue.close()
            RuneRunningCoordinator.complete(agreement, bot)
            delivery = null
        }
        if (!success) return failedAttempt()
        if (agreement.dialogue.hostFinished) {
            bankEarnings()
            return true
        }
        failures = 0
        prepared = false
        return false
    }

    /**
     * Banks earned runes and unnotes replacement essence through ordinary banking controls.
     * Nature trips carry their real return fare; Cosmic trips retain their owned dramen staff for native entry.
     */
    private suspend fun pack(): Boolean = withTimeoutOrNull(RuneRunningCoordinator.tripTimeout(altar)) {
        val previous = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        try {
            if (!isActionSafe(ActionSafety.NON_COMBAT) || !openBank() ||
                !handler.banking.depositInventory() || !handler.equipment.unequipAll() ||
                !handler.banking.depositInventory()) return@withTimeoutOrNull false
            handler.banking.clickBankingMode(false)
            if (altar == Altar.COSMIC && (!handler.banking.withdraw(Item(Zanaris.STAFF)) ||
                    !bot.inventory.contains(Zanaris.STAFF))) return@withTimeoutOrNull false
            if (altar == Altar.NATURE) {
                if (bot.bank.computeAmountForId(COINS) < NATURE_FARE ||
                    !handler.banking.withdraw(Item(COINS, NATURE_FARE)) ||
                    bot.inventory.computeAmountForId(COINS) != NATURE_FARE) return@withTimeoutOrNull false
            }
            if (bot.bank.computeAmountForId(essence) >= BATCH) {
                if (!handler.banking.withdraw(Item(essence, BATCH)) ||
                    bot.inventory.computeAmountForId(essence) != BATCH) return@withTimeoutOrNull false
            } else if (seedUsed) return@withTimeoutOrNull false
            isActionSafe(ActionSafety.NON_COMBAT)
        } finally {
            if (bot.bank.isOpen) handler.banking.clickBankingMode(previous != 0)
            bot.overlays.closeWindows()
        }
    } == true

    /**
     * A failed trip cannot monopolize the base activity forever. Owned partial stock remains bankable.
     */
    private suspend fun failedAttempt(): Boolean {
        prepared = false
        failures++
        if (failures >= 3 || ending()) {
            failed = failures >= 3
            bankEarnings()
            return true
        }
        delay(5.seconds)
        return false
    }

    private suspend fun bankEarnings() {
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
     * Nature's supported surface loop uses Draynor rather than treating the closed Shilo gate as bank access.
     */
    private suspend fun openBank(): Boolean {
        if (altar == Altar.NATURE && !handler.webWalkTo(Zone.DRAYNOR, bank = BankAccess.NONE, fallback = false))
            return false
        return handler.banking.travelToBankOpen()
    }

    override fun paused() {
        delivery?.dialogue?.close()
        BotConversations.cancel(bot)
        RuneRunningCoordinator.release(bot)
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() {
        delivery?.dialogue?.close()
        BotConversations.cancel(bot)
        delivery?.let { RuneRunningCoordinator.complete(it, bot) }
        delivery = null
        RuneRunningCoordinator.release(bot)
        if (!isPaused()) {
            if (introductory && (failed || bot.combatLevel >= 50)) bot.preferences.runeRunning.completeIntroduction()
            if (guardsInstalled) {
                bot.combat.isDisabled = previousCombatDisabled
                guardsInstalled = false
            }
        }
    }

    override suspend fun completed() {
        if (introductory && bot.preferences.runeRunning.isIntroductory) return
        MerchantingCoordinator.getSellingScript(bot)?.let { bot.scriptStack.softPushHead(it) }
    }

    override fun snapshot(): BotScriptData = RunnerData().also {
        it.altar = altar
        it.essenceId = essence
        it.introductory = introductory
        it.deadline = deadline
        it.seedUsed = seedUsed
        it.introducedHost = introducedHost
    }

    /**
     * Persists intent and the consumed starter gift; live partners and trade interfaces are always rebuilt.
     *
     * @author lare96
     */
    class RunnerData : BotScriptData() {
        var altar = Altar.AIR
        var essenceId = 1436
        var introductory = false
        var deadline = 0L
        var seedUsed = false
        var introducedHost: String? = null

        override fun load(data: JsonObject) {
            altar = Altar.valueOf(data.get("altar").asString)
            essenceId = data.get("essenceId")?.asInt ?: RuneRunningCoordinator.legacyEssence(altar)
            introductory = data.get("introductory")?.asBoolean ?: false
            deadline = data.get("deadline")?.asLong ?: 0L
            seedUsed = data.get("seedUsed")?.asBoolean ?: false
            introducedHost = data.get("introducedHost")?.takeUnless { it.isJsonNull }?.asString
        }

        override fun save(data: JsonObject) {
            data.addProperty("altar", altar.name)
            data.addProperty("essenceId", essenceId)
            data.addProperty("introductory", introductory)
            data.addProperty("deadline", deadline)
            data.addProperty("seedUsed", seedUsed)
            data.addProperty("introducedHost", introducedHost)
        }
    }
}
