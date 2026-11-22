package game.bot.scripts

import api.bot.Suspendable.delay
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
import engine.bot.coordinator.RuneRunningCoordinator.Route
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
 * Delivers one loose load from a bank and returns there to unnote the replacement.
 * Each confirmed delivery earns an agreed fee from the crafter's carried coins.
 *
 * @author lare96
 */
class RuneRunnerBotScript(bot: Bot, data: RunnerData) : BotScript(bot), SocialBotScript {

    val altar = data.altar
    val essenceId = data.essenceId
    val introductory = data.introductory
    internal val minimumFee = maxOf(data.minimumFee, RuneRunningCoordinator.fare(altar) + 1)
    internal var paidTrips = data.paidTrips
    private val route = Route(altar, essenceId)
    private val deadline = data.deadline
    private var introducedHost = data.introducedHost
    private var prepared = false
    private var failures = 0
    private var delivery: RuneRunningCoordinator.Delivery? = null
    private var previousCombatDisabled: Boolean? = null

    constructor(bot: Bot, altar: Altar, introductory: Boolean, duration: Duration,
                essenceId: Int = RuneRunningCoordinator.essence(altar), minimumFee: Int = 1) : this(bot, RunnerData().also {
        it.altar = altar
        it.essenceId = essenceId
        it.introductory = introductory
        it.deadline = System.currentTimeMillis() + duration.inWholeMilliseconds
        it.minimumFee = minimumFee
    })

    override val allowsCombatResponse = false
    override val actionSafety = ActionSafety.COMBAT

    override fun allowsLoot(item: GroundItem): Boolean = false

    override fun canSocialize(): Boolean = delivery?.dialogue?.canSocialize(bot) == true

    override suspend fun init(resumed: Boolean): Boolean {
        if (ending() || !RuneRunningCoordinator.attach(this)) return true
        previousCombatDisabled = bot.combat.isExplicitlyDisabled
        bot.combat.isDisabled = true
        bot.combat.target = null
        prepared = bot.inventory.computeAmountForId(essenceId) == BATCH &&
            RuneRunningCoordinator.atMeeting(bot, route)
        return false
    }

    private fun ending(): Boolean = System.currentTimeMillis() >= deadline || bot.combatLevel >= 50 ||
        introductory && !bot.preferences.runeRunning.isIntroductory

    override suspend fun run(): Boolean {
        bot.combat.target = null
        if (bot.combat.inCombat()) {
            RuneRunningCoordinator.release(this)
            withTimeoutOrNull(30.seconds) {
                while (bot.isAlive && bot.combat.inCombat()) {
                    handler.combat.fleeCombat()
                    delay(1.seconds)
                }
            }
            bankEarnings()
            return true
        }
        if (ending() || !RuneRunningCoordinator.hasWork(this)) {
            bankEarnings()
            return true
        }
        if (!prepared) {
            if (!pack()) return failedAttempt()
            prepared = true
        }
        val reached = withTimeoutOrNull(RuneRunningCoordinator.tripTimeout(altar)) {
            handler.webWalkTo(altar.exit, radius = 2, attempts = WEB_WALK_ATTEMPTS,
                bank = BankAccess.NONE, fallback = false)
        } == true
        if (!reached || !isActionSafe(ActionSafety.NON_COMBAT)) return failedAttempt()

        val agreement = withTimeoutOrNull(2.minutes) {
            while (!ending() && RuneRunningCoordinator.hasWork(this@RuneRunnerBotScript) &&
                isActionSafe(ActionSafety.NON_COMBAT)) {
                RuneRunningCoordinator.requestDelivery(this@RuneRunnerBotScript)?.let { return@withTimeoutOrNull it }
                delay(2.seconds)
            }
            null
        } ?: return failedAttempt()
        delivery = agreement
        try {
            if (agreement.dialogue.beforeTrade(bot, introducedHost != agreement.crafter.username)) {
                introducedHost = agreement.crafter.username
                RuneRunningTrade.exchange(bot, agreement.crafter, agreement.runnerGives, agreement.crafterGives) {
                    !ending() && RuneRunningCoordinator.valid(agreement)
                }
                agreement.dialogue.afterTrade(bot)
            }
        } finally {
            RuneRunningCoordinator.finishDelivery(agreement, bot)
            delivery = null
        }
        if (!agreement.paid) return failedAttempt()
        failures = 0
        prepared = false
        return false
    }

    private suspend fun pack(): Boolean = withTimeoutOrNull(RuneRunningCoordinator.tripTimeout(altar)) {
        val previous = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        try {
            if (!isActionSafe(ActionSafety.NON_COMBAT) || !openBank() ||
                !handler.banking.depositInventory() || !handler.equipment.unequipAll() ||
                !handler.banking.depositInventory()) return@withTimeoutOrNull false
            handler.banking.clickBankingMode(false)
            val fare = RuneRunningCoordinator.fare(altar)
            if (fare > 0 && !handler.banking.withdraw(Item(COINS, fare))) return@withTimeoutOrNull false
            if (!handler.banking.withdraw(Item(essenceId, BATCH)) ||
                bot.inventory.computeAmountForId(essenceId) != BATCH) return@withTimeoutOrNull false
        } finally {
            if (bot.bank.isOpen) handler.banking.clickBankingMode(previous != 0)
            bot.overlays.closeWindows()
        }
        isActionSafe(ActionSafety.NON_COMBAT)
    } == true

    private suspend fun failedAttempt(): Boolean {
        prepared = prepared && bot.inventory.computeAmountForId(essenceId) == BATCH
        if (++failures >= 2 || ending() || !RuneRunningCoordinator.hasWork(this)) {
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

    private suspend fun openBank(): Boolean {
        if (bot.bank.isOpen) return true
        if (altar == Altar.NATURE && !handler.webWalkTo(Zone.DRAYNOR, bank = BankAccess.NONE, fallback = false))
            return false
        val bank = handler.webWalkToNearestBank(attempts = WEB_WALK_ATTEMPTS, fallback = false) ?: return false
        return handler.banking.open(bank)
    }

    override fun paused() {
        RuneRunningCoordinator.release(this)
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() {
        RuneRunningCoordinator.release(this)
        previousCombatDisabled?.let { bot.combat.isDisabled = it }
        previousCombatDisabled = null
    }

    override fun snapshot(): BotScriptData = RunnerData().also {
        it.altar = altar
        it.essenceId = essenceId
        it.introductory = introductory
        it.deadline = deadline
        it.minimumFee = minimumFee
        it.paidTrips = paidTrips
        it.introducedHost = introducedHost
    }

    /**
     * Stores route, deadline and paid trip count. The current partner and native trade are session-local.
     *
     * @author lare96
     */
    class RunnerData : BotScriptData() {
        var altar = Altar.AIR
        var essenceId = 1436
        var introductory = false
        var deadline = 0L
        var minimumFee = 1
        var paidTrips = 0
        var introducedHost: String? = null

        override fun load(data: JsonObject) {
            altar = Altar.valueOf(data.get("altar").asString)
            essenceId = data.get("essenceId")?.asInt ?: RuneRunningCoordinator.legacyEssence(altar)
            introductory = data.get("introductory")?.asBoolean ?: false
            deadline = data.get("deadline")?.asLong ?: 0L
            minimumFee = data.get("minimumFee")?.asInt ?: 1
            paidTrips = (data.get("paidTrips")?.asInt ?: 0).coerceIn(0, RuneRunningCoordinator.MAX_TRIPS_PER_RUNNER)
            introducedHost = data.get("introducedHost")?.takeUnless { it.isJsonNull }?.asString
        }

        override fun save(data: JsonObject) {
            data.addProperty("altar", altar.name)
            data.addProperty("essenceId", essenceId)
            data.addProperty("introductory", introductory)
            data.addProperty("deadline", deadline)
            data.addProperty("minimumFee", minimumFee)
            data.addProperty("paidTrips", paidTrips)
            data.addProperty("introducedHost", introducedHost)
        }
    }
}
