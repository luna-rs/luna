package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.action.PkRiskBudget
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.Zone
import api.combat.death.PlayerDeathItems
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import engine.bot.coordinator.MerchantingCoordinator
import engine.controllers.WildernessLocatableController.levelAt
import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.combat.PkBotScript.PkArea
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference.Purpose
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Searches existing Wilderness hotspots while the shared loot reflex performs native pickups.
 * New accounts spend a persisted introductory allowance here; supply-starved PKers make bounded detours.
 * Both begin in low Edgeville, bank their equipment and loot, never initiate combat, and leave on danger.
 * Roaming uses existing anchors and webwalking. A saved SKILL identity retains its Edgeville boundary.
 *
 * @property introductory Whether the one-off level-three account allowance owns this visit.
 * @property deadline Absolute expiry for this visit; offline time cannot renew a saved visit.
 * @author lare96
 */
class WildernessScavengeBotScript(bot: Bot, data: ScavengeData) : BotScript(bot) {
    /**
     * Whether the one-off new-account phase owns this visit instead of a failed-PK detour.
     */
    val introductory = data.introductory

    /**
     * Absolute visit expiry. The separate introductory allowance excludes time offline.
     */
    private val deadline = data.deadline

    /**
     * Starts a bounded visit without replacing the bot's permanent preferences or PK identity.
     */
    constructor(bot: Bot, introductory: Boolean, duration: Duration) :
        this(bot, ScavengeData().also {
            it.introductory = introductory
            it.deadline = System.currentTimeMillis() + duration.inWholeMilliseconds
        })

    override val actionSafety = ActionSafety.COMBAT

    override val allowsCombatResponse = false

    /**
     * The current walking goal survives reflex pauses and relogging.
     */
    private var destination: Position? = data.destination

    /**
     * Periodic return checks keep Edgeville preferred after exploring a quiet Wilderness.
     */
    private var nextEdgevilleCheck = data.nextEdgevilleCheck

    /**
     * Only the actual scavenging phase accepts reactive loot; preparation, banking and escapes do not.
     */
    private var collecting = false
    /**
     * Live banking completion; restored visits always pack again before departure.
     */
    private var prepared = false
    /**
     * Bounded banking failures end an introductory phase instead of repeatedly selecting a broken visit.
     */
    private var bankingFailures = 0
    /**
     * Native attack suppression survives loot pauses and is restored when this owner finally exits.
     */
    private var guardsInstalled = false
    private var previousCombatDisabled = false

    override suspend fun init(resumed: Boolean): Boolean {
        if (!guardsInstalled) {
            previousCombatDisabled = bot.combat.isDisabled
            bot.combat.isDisabled = true
            guardsInstalled = true
        }
        bot.combat.target = null
        return false
    }

    /**
     * Uses a gradual 8-to-48 Wilderness-level appetite. Low confidence and high intelligence stay shallow;
     * confidence and low intelligence permit deeper searches. SKILL PK detours remain inside Edgeville.
     */
    internal fun maximumDepth(): Int {
        if (!introductory && bot.preferences.pk?.purpose() == Purpose.SKILL) return 8
        return maximumDepth(bot.personality.confidence, bot.personality.intelligence)
    }

    /**
     * Keeps pickup and roaming destinations within surface Wilderness and the current identity boundary.
     */
    internal fun allowsPosition(position: Position): Boolean = position.z == 0 && position.y in 3520..3966 &&
        levelAt(position) in 1..maximumDepth() &&
        (introductory || bot.preferences.pk?.purpose() != Purpose.SKILL || position.regionId == EDGEVILLE_REGION)

    override fun allowsLoot(item: GroundItem): Boolean = collecting && !ending() &&
        bot.state == EntityState.ACTIVE && bot.healthPercent >= 50 && !bot.isLocked && !bot.combat.inCombat() &&
        allowsPosition(bot.position) && allowsPosition(item.position) && item.state == EntityState.ACTIVE

    /**
     * Introductory completion changes activity choice, never skills. Detours obey their saved visit expiry.
     */
    private fun ending(): Boolean = System.currentTimeMillis() >= deadline ||
        (introductory && (!bot.preferences.scavenging.isIntroductory || bot.combatLevel != 3))

    /**
     * Applies the existing native death-loss assessment with a scavenger's personality allowance.
     * A level-three account need not be assigned a PK identity to bank accumulated risk.
     */
    private fun withinRiskBudget(): Boolean {
        val share = PkRiskBudget.basisPoints(bot.personality.confidence, bot.personality.intelligence,
            bot.preferences.activities[io.luna.game.model.mob.bot.brain.BotActivity.PKING] ?: 0.0)
        return PkRiskBudget.assessor(bot, PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode()), share)(
            bot.inventory.filterNotNull(), bot.equipment.filterNotNull(), false).withinBudget
    }

    override suspend fun run(): Boolean {
        if (bot.combat.inCombat() || bot.healthPercent < 50 || ending()) {
            collecting = false
            bot.combat.target = null
            if (bot.healthPercent < 65) handler.inventory.eatAnyFood()
            if (bot.inWilderness() && !handler.combat.fleeWilderness()) return false
            if (bot.combat.inCombat()) return false
            prepared = false
            destination = null
            if (ending()) {
                bankLoot()
                return true
            }
        }
        if (!prepared || bot.inventory.isFull || !withinRiskBudget()) {
            collecting = false
            if (bot.inWilderness() && !handler.combat.fleeWilderness()) return false
            if (!bankLoot()) {
                bankingFailures++
                if (bankingFailures >= 3) {
                    if (introductory) bot.preferences.scavenging.completeIntroduction()
                    return true
                }
                delay(10.seconds)
                return false
            }
            bankingFailures = 0
            prepared = true
            destination = null
            // Re-enter by walking from the existing Edgeville zone, never by a deep-Wilderness teleport.
            if (!handler.webWalkTo(zone = Zone.EDGEVILLE, bank = BankAccess.NONE)) {
                prepared = false
                delay(10.seconds)
                return false
            }
        }
        if (!ActionSafety.NON_COMBAT.allows(bot)) return false
        collecting = true
        val now = System.currentTimeMillis()
        if (now >= nextEdgevilleCheck) {
            nextEdgevilleCheck = now + 2.minutes.inWholeMilliseconds
            destination = edgevilleAnchors().random()
        }
        val goal = destination?.takeIf(::allowsPosition) ?: chooseDestination().also { destination = it }
        if (bot.position.isWithinDistance(goal, 3)) {
            destination = null
            // Loiter while the loot reflex watches public drops and native death processing.
            delay(rand(8, 20).seconds)
            return false
        }
        val reached = withTimeoutOrNull(45.seconds) {
            handler.webWalkTo(goal, bank = BankAccess.NONE)
        } == true
        if (reached || bot.combat.inCombat()) destination = null
        else {
            destination = null
            delay(5.seconds)
        }
        return false
    }

    /**
     * Uses existing PK anchors instead of manufacturing walkable coordinates. Visible Edgeville activity
     * keeps searches local; a quiet area opens personality-bounded alternatives before the next return.
     */
    private fun chooseDestination(): Position {
        val edgevilleActive = world.players.any {
            it !== bot && it.state == EntityState.ACTIVE && it.position.regionId == EDGEVILLE_REGION &&
                levelAt(it.position) > 0 && (it !is Bot || !it.preferences.scavenging.isIntroductory)
        }
        val roaming = if (introductory) PkArea.entries.flatMap { it.anchors }
            else PkBotScript.patrolGroups(bot).flatten()
        val anchors = if (edgevilleActive) edgevilleAnchors() else roaming
            .filter(::allowsPosition).ifEmpty { edgevilleAnchors() }
        return anchors.random()
    }

    /**
     * Deposits carried valuables and equipment before withdrawing up to four already-owned food items.
     * Food is helpful but not required for a penniless level-three scavenger; none is created or purchased.
     */
    private suspend fun bankLoot(): Boolean {
        collecting = false
        if (bot.inWilderness() || !ActionSafety.NON_COMBAT.allows(bot)) return false
        try {
            if (!handler.banking.travelToBankOpen() || !ActionSafety.NON_COMBAT.allows(bot)) return false
            if (!handler.banking.depositInventory() || !ActionSafety.NON_COMBAT.allows(bot)) return false
            if (!handler.equipment.unequipAll() || !ActionSafety.NON_COMBAT.allows(bot)) return false
            if (!handler.banking.depositInventory() || !ActionSafety.NON_COMBAT.allows(bot)) return false
            if (!ending()) handler.banking.withdrawAnyFood(4, minimumHeal = 1, retry = false)
            // A nearly penniless account must not endlessly repack the same unaffordable food risk.
            if (!withinRiskBudget() && !handler.banking.depositInventory()) return false
            return ActionSafety.NON_COMBAT.allows(bot)
        } finally {
            bot.overlays.closeWindows()
        }
    }

    override fun paused() {
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() {
        if (!isPaused() && guardsInstalled) {
            collecting = false
            bot.combat.isDisabled = previousCombatDisabled
            guardsInstalled = false
        }
    }

    override suspend fun completed() {
        // A short introductory visit returns to scavenging until the account's whole allowance is spent.
        if (introductory && bot.preferences.scavenging.isIntroductory) return
        MerchantingCoordinator.getSellingScript(bot)?.let { bot.scriptStack.softPushHead(it) }
    }

    override fun snapshot() = ScavengeData().also {
        it.introductory = introductory
        it.deadline = deadline
        it.destination = destination
        it.nextEdgevilleCheck = nextEdgevilleCheck
    }

    /**
     * Saved visit intent; physical equipment and bank state are always checked again on restoration.
     *
     * @author lare96
     */
    class ScavengeData : BotScriptData() {
        /**
         * Visit entry point: introductory phase or optional PK detour.
         */
        var introductory = false
        /**
         * Absolute visit expiry, retained across saves.
         */
        var deadline = 0L
        /**
         * Last walking goal, revalidated against current personality and purpose on the next cycle.
         */
        var destination: Position? = null
        /**
         * Next scheduled Edgeville return check.
         */
        var nextEdgevilleCheck = 0L

        override fun load(data: JsonObject) {
            introductory = data.get("introductory").asBoolean
            deadline = data.get("deadline").asLong
            nextEdgevilleCheck = data.get("nextEdgevilleCheck")?.asLong ?: 0
            destination = data.getAsJsonObject("destination")?.let {
                Position(it.get("x").asInt, it.get("y").asInt, it.get("z").asInt)
            }
        }

        override fun save(data: JsonObject) {
            data.addProperty("introductory", introductory)
            data.addProperty("deadline", deadline)
            data.addProperty("nextEdgevilleCheck", nextEdgevilleCheck)
            destination?.let { position ->
                data.add("destination", JsonObject().apply {
                    addProperty("x", position.x)
                    addProperty("y", position.y)
                    addProperty("z", position.z)
                })
            }
        }
    }

    companion object {
        /**
         * Existing SKILL PK footprint immediately north of Edgeville.
         */
        private const val EDGEVILLE_REGION = 12343

        /**
         * Pure depth policy, kept independent of live navigation and item stock.
         */
        internal fun maximumDepth(confidence: Double, intelligence: Double): Int {
            fun normalized(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.5
            val appetite = (normalized(confidence) + 1 - normalized(intelligence)) / 2
            return 8 + (40 * appetite).roundToInt()
        }

        /**
         * Reuses the PK runner's supplied low-level anchors within its Edgeville SKILL region.
         */
        private fun edgevilleAnchors(): List<Position> = PkBotScript.PkArea.LOW_LEVEL.anchors.filter {
            it.regionId == EDGEVILLE_REGION && it.z == 0 && levelAt(it) > 0
        }
    }
}
