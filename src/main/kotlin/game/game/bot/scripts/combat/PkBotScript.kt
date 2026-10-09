package game.bot.scripts.combat

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.LocatableDistanceComparator
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.movement.NavigationResult
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Wilderness PK bot behaviour script.
 *
 * This script prepares owned food for an already equipped melee bot, enters the lower Wilderness and searches for
 * valid player targets. The deadline survives combat pauses. Expiry, missing supplies and repeated failures trigger
 * bounded walking escape attempts; no travel fallback bypasses normal Wilderness restrictions.
 *
 * Movement is currently based on manually verified anchor points. Future versions should move more of this into the
 * zone/area system so bots can recognize caves, gates, one-way routes, high-risk paths, and special escape cases.
 *
 * @param bot The bot controlled by this script.
 * @param duration The maximum amount of time this script should actively PK before fleeing.
 * @author lare96
 */
class PkBotScript(bot: Bot, val duration: Duration) : BotScript(bot) {

    // TODO@0.5.0 This should start the bot at 1 of 4 banks: FALADOR, VARROCK, EDGEVILLE, HOME (DRAYNOR) depending on
    //  distance. Then it should cache that bank and use that as the home bank for PKing activities.
    // TODO@0.5.0 Anchor points for entering the wild from those banks (or use LOW_LEVEL_ANCHOR_POINTS sorted by distance?).
    // TODO@1.0 dynamic "clans", bots will advertise at edgeville they're looking to start or join and then they wil pk together
    // TODO do not attack liked players when searching, UNLESS we're greedy and unkind. then we attack everyone
    // TODO PKers who are kind only attack other pkers (you can check which script they have!). Unkind PKers go for everyone.
    // TODO Pking groups with liked bots? Pking groups are persisted and permanent, each bot clan is assigned to a team cape.
    // TODO each bot clan has a leader which determines who can/can't join their clan (must be liked/neutral/etc. by leader)
    //  bots will not attack anyone wearing their team cape.
    // TODO there's currently 50 team capes meaning 50 possible clans for bots to join. chatgpt needs to generate:
    //  clan name, description, favorite wilderness subzones (complete
    // TODO bots will automatically be placed in a clan when PKing for the first time. bots will not attack other bots
    //  wearing the same team cape. team capes are auto-spawn equipped on bots during loadout selection. bots can only be
    //  placed once. once the bot decides either to join a clan or go solo for the first time the choice is persisted and
    // can't be changed (only in file)

    /**
     * Shared PK bot constants.
     */
    companion object {

        /**
         * Low-level Wilderness anchor points used for entering, escaping, and returning toward safer Wilderness levels.
         *
         * These are spread across the lower Wilderness so bots do not always enter or escape through the exact same
         * tile.
         */
        val LOW_LEVEL_ANCHOR_POINTS = listOf(
            Position(2978, 3607, 0),
            Position(3030, 3611, 0),
            Position(3079, 3620, 0),
            Position(3100, 3633, 0),
            Position(3164, 3616, 0),
            Position(3198, 3618, 0),
            Position(3234, 3637, 0),
            Position(3274, 3612, 0),
            Position(3331, 3640, 0),
            Position(3367, 3641, 0),
            Position(3162, 3668, 0),
            Position(3251, 3662, 0),
            Position(3310, 3672, 0),
            Position(3060, 3662, 0),
            Position(3020, 3667, 0),
            Position(2987, 3672, 0),
            Position(2969, 3667, 0),
            Position(3102, 3671, 0),
            Position(3143, 3677, 0)
        )

        internal const val MAX_SESSION_MS = 30 * 60 * 1000L
        private const val START_FOOD = 8
        private const val MINIMUM_HEAL = 10
        private const val MAX_ROUTE_FAILURES = 3
        private const val MAX_EMPTY_SEARCHES = 60
        private const val MAX_ESCAPE_ATTEMPTS = 3

        /** Eligible profiles already wear melee gear and own enough food for a bounded session. */
        internal fun isEligible(bot: Bot): Boolean = bot.isAlive && bot.state == EntityState.ACTIVE &&
            bot.position.z == 0 && !bot.inWilderness() && !bot.combat.isDisabled && hasMeleeEquipment(bot) &&
            foodCount(bot.inventory) + foodCount(bot.bank) >= START_FOOD

        private fun hasMeleeEquipment(bot: Bot): Boolean = bot.equipment.weapon != null &&
            bot.equipment[Equipment.CHEST] != null && bot.equipment[Equipment.LEGS] != null &&
            !bot.combat.weapon.isRanged && bot.combat.weapon.type != Weapon.STAFF &&
            !bot.combat.magic.isAutocasting

        private fun foodCount(items: Iterable<Item?>): Long = items.sumOf { item ->
            if (item != null && (Food.ID_TO_FOOD[item.id]?.heal ?: 0) >= MINIMUM_HEAL) item.amount.toLong() else 0L
        }

        class PkData : BotScriptData() {
            var duration = Duration.ZERO
            var returning = false
            var routeFailures = 0
            var emptySearches = 0
            var escapeAttempts = 0
            override fun load(data: JsonObject) {
                duration = (data.get("duration")?.asLong ?: 0).coerceIn(0, MAX_SESSION_MS).milliseconds
                returning = data.get("returning")?.asBoolean ?: false
                routeFailures = data.get("routeFailures")?.asInt ?: 0
                emptySearches = data.get("emptySearches")?.asInt ?: 0
                escapeAttempts = data.get("escapeAttempts")?.asInt ?: 0
            }

            override fun save(data: JsonObject) {
                data.addProperty("duration", duration.inWholeMilliseconds.coerceIn(0, MAX_SESSION_MS))
                data.addProperty("returning", returning)
                data.addProperty("routeFailures", routeFailures)
                data.addProperty("emptySearches", emptySearches)
                data.addProperty("escapeAttempts", escapeAttempts)
            }
        }
    }

    /**
     * Common Wilderness PK areas that bots can travel through while looking for targets.
     *
     * Each area contains a set of region ids used for rough location grouping and a list of anchor points used for
     * wandering, routing, and hotspot selection.
     *
     * @property regions The map region ids covered by this PK area.
     * @property anchors Known walkable anchor points inside or near this PK area.
     */
    enum class PkArea(val regions: Set<Int>, val anchors: List<Position>) {

        /**
         * Lower Wilderness routes near the ditch and early Wilderness combat levels.
         */
        LOW_LEVEL(regions = setOf(11831, 12087, 12343, 12599, 12855, 13111),
                  anchors = listOf(Position(2962, 3558, 0),
                                   Position(2984, 3563, 0),
                                   Position(2998, 3567, 0),
                                   Position(3023, 3570, 0),
                                   Position(3039, 3561, 0),
                                   Position(3052, 3549, 0),
                                   Position(3067, 3542, 0),
                                   Position(3088, 3542, 0),
                                   Position(3096, 3547, 0),
                                   Position(3091, 3528, 0),
                                   Position(3106, 3529, 0),
                                   Position(3131, 3543, 0),
                                   Position(3163, 3533, 0),
                                   Position(3193, 3533, 0),
                                   Position(3209, 3548, 0),
                                   Position(3182, 3554, 0),
                                   Position(3194, 3571, 0),
                                   Position(3221, 3558, 0),
                                   Position(3234, 3542, 0),
                                   Position(3254, 3548, 0),
                                   Position(3274, 3554, 0),
                                   Position(3287, 3539, 0),
                                   Position(3300, 3554, 0),
                                   Position(3308, 3566, 0))),

        /**
         * Level-10 Chaos Temple area and the nearby low-to-mid Wilderness routes.
         */
        CHAOS_TEMPLE_LVL_10(regions = setOf(12856),
                            anchors = listOf(Position(3207, 3587, 0),
                                             Position(3210, 3610, 0),
                                             Position(3217, 3629, 0),
                                             Position(3234, 3638, 0),
                                             Position(3255, 3632, 0),
                                             Position(3238, 3620, 0),
                                             Position(3241, 3612, 0),
                                             Position(3238, 3600, 0),
                                             Position(3227, 3609, 0),
                                             Position(3229, 3587, 0))),

        /**
         * Graveyard of Shadows hotspot and surrounding level-20s Wilderness movement routes.
         */
        GRAVEYARD_OF_SHADOWS(regions = setOf(12601),
                             anchors = listOf(Position(3140, 3705, 0),
                                              Position(3151, 3697, 0),
                                              Position(3160, 3707, 0),
                                              Position(3176, 3695, 0),
                                              Position(3189, 3699, 0),
                                              Position(3193, 3676, 0),
                                              Position(3192, 3655, 0),
                                              Position(3182, 3651, 0),
                                              Position(3145, 3654, 0),
                                              Position(3144, 3668, 0),
                                              Position(3154, 3670, 0),
                                              Position(3163, 3670, 0),
                                              Position(3178, 3671, 0),
                                              Position(3172, 3678, 0),
                                              Position(3185, 3672, 0))),

        /**
         * Green dragon hotspot routes near the western level-20s Wilderness.
         */
        GREEN_DRAGONS(regions = setOf(12345, 12601),
                      anchors = listOf(Position(3078, 3707, 0),
                                       Position(3100, 3707, 0),
                                       Position(3102, 3698, 0),
                                       Position(3120, 3699, 0),
                                       Position(3118, 3685, 0),
                                       Position(3132, 3695, 0),
                                       Position(3143, 3701, 0),
                                       Position(3155, 3706, 0),
                                       Position(3153, 3699, 0),
                                       Position(3170, 3708, 0),
                                       Position(3176, 3696, 0),
                                       Position(3188, 3699, 0),
                                       Position(3082, 3681, 0))),

        /**
         * Bandit Camp and nearby west Wilderness routes.
         */
        BANDIT_CAMP(regions = setOf(12089),
                    anchors = listOf(Position(3016, 3657, 0),
                                     Position(3016, 3674, 0),
                                     Position(3017, 3702, 0),
                                     Position(3056, 3660, 0),
                                     Position(3068, 3666, 0),
                                     Position(3068, 3691, 0),
                                     Position(3039, 3698, 0),
                                     Position(3030, 3705, 0),
                                     Position(3050, 3708, 0),
                                     Position(3037, 3673, 0),
                                     Position(3038, 3653, 0))),

        /**
         * Forgotten Cemetery hotspot and surrounding level-30s Wilderness routes.
         */
        THE_FORGOTTEN_CEMETERY(regions = setOf(11834),
                               anchors = listOf(Position(2958, 3718, 0),
                                                Position(2973, 3723, 0),
                                                Position(2989, 3716, 0),
                                                Position(2999, 3720, 0),
                                                Position(3003, 3731, 0),
                                                Position(3003, 3744, 0),
                                                Position(3002, 3769, 0),
                                                Position(2966, 3772, 0),
                                                Position(2953, 3754, 0),
                                                Position(2966, 3750, 0),
                                                Position(2984, 3752, 0),
                                                Position(2976, 3735, 0),
                                                Position(2981, 3734, 0))),

        /**
         * Western level-20 ruins routes.
         */
        RUINS_LVL_20(regions = setOf(11833),
                     anchors = listOf(Position(2960, 3653, 0),
                                      Position(2969, 3656, 0),
                                      Position(2993, 3653, 0),
                                      Position(3002, 3664, 0),
                                      Position(3000, 3676, 0),
                                      Position(2993, 3695, 0),
                                      Position(2980, 3705, 0),
                                      Position(2967, 3695, 0),
                                      Position(2997, 3701, 0),
                                      Position(2981, 3670, 0))),

        /**
         * Central level-30 ruins routes near Web Chasm.
         */
        RUINS_LVL_30(regions = setOf(12602),
                     anchors = listOf(Position(3142, 3715, 0),
                                      Position(3156, 3725, 0),
                                      Position(3167, 3727, 0),
                                      Position(3179, 3734, 0),
                                      Position(3162, 3740, 0),
                                      Position(3146, 3751, 0),
                                      Position(3148, 3762, 0),
                                      Position(3154, 3767, 0),
                                      Position(3179, 3765, 0),
                                      Position(3188, 3754, 0),
                                      Position(3167, 3755, 0),
                                      Position(3192, 3743, 0),
                                      Position(3193, 3730, 0),
                                      Position(3193, 3719, 0),
                                      Position(3179, 3717, 0),
                                      Position(3162, 3715, 0),
                                      Position(3154, 3715, 0))),

        /**
         * Deep-west Chaos Temple routes near the level-40 Wilderness area.
         */
        CHAOS_TEMPLE_LVL_40(regions = setOf(11835),
                            anchors = listOf(Position(2955, 3785, 0),
                                             Position(2976, 3788, 0),
                                             Position(2988, 3796, 0),
                                             Position(2996, 3814, 0),
                                             Position(2999, 3821, 0),
                                             Position(3000, 3832, 0),
                                             Position(2984, 3832, 0),
                                             Position(2966, 3834, 0),
                                             Position(2951, 3832, 0),
                                             Position(2960, 3825, 0),
                                             Position(2979, 3821, 0),
                                             Position(2983, 3815, 0),
                                             Position(2962, 3807, 0),
                                             Position(2961, 3798, 0),
                                             Position(2951, 3802, 0),
                                             Position(2959, 3819, 0),
                                             Position(2952, 3820, 0))),

        /**
         * Lava Maze and surrounding deep Wilderness routes.
         */
        LAVA_MAZE(regions = setOf(12092, 12348, 12091, 12347),
                  anchors = listOf(Position(3021, 3875, 0),
                                   Position(3040, 3887, 0),
                                   Position(3030, 3894, 0),
                                   Position(3047, 3895, 0),
                                   Position(3059, 3889, 0),
                                   Position(3045, 3876, 0),
                                   Position(3067, 3894, 0),
                                   Position(3084, 3900, 0),
                                   Position(3082, 3886, 0),
                                   Position(3101, 3893, 0),
                                   Position(3103, 3900, 0),
                                   Position(3119, 3894, 0),
                                   Position(3128, 3889, 0),
                                   Position(3123, 3873, 0),
                                   Position(3126, 3862, 0),
                                   Position(3131, 3846, 0),
                                   Position(3130, 3829, 0),
                                   Position(3120, 3823, 0),
                                   Position(3118, 3815, 0),
                                   Position(3115, 3793, 0),
                                   Position(3100, 3785, 0),
                                   Position(3091, 3797, 0),
                                   Position(3094, 3806, 0),
                                   Position(3095, 3816, 0),
                                   Position(3079, 3804, 0),
                                   Position(3076, 3808, 0),
                                   Position(3075, 3787, 0),
                                   Position(3065, 3794, 0),
                                   Position(3064, 3800, 0),
                                   Position(3065, 3813, 0),
                                   Position(3046, 3820, 0),
                                   Position(3020, 3820, 0),
                                   Position(3025, 3804, 0),
                                   Position(3035, 3790, 0),
                                   Position(3039, 3800, 0),
                                   Position(3026, 3829, 0))),

        /**
         * Demonic Ruins routes in the north-east deep Wilderness.
         */
        DEMONIC_RUINS(regions = setOf(13116),
                      anchors = listOf(Position(3271, 3894, 0),
                                       Position(3283, 3897, 0),
                                       Position(3298, 3895, 0),
                                       Position(3310, 3887, 0),
                                       Position(3322, 3877, 0),
                                       Position(3278, 3867, 0),
                                       Position(3277, 3875, 0),
                                       Position(3276, 3879, 0),
                                       Position(3277, 3886, 0)));

        // TODO@1.0 Add deep wilderness locations once doors/gates are completed.

        /**
         * Immutable list of all configured PK areas.
         */
        companion object {
            val ALL = ImmutableList.copyOf(values())
        }
    }

    /** Absolute session deadline in milliseconds; combat pauses do not renew it. */
    var expireAt: Long = System.currentTimeMillis() + duration.inWholeMilliseconds.coerceIn(0, MAX_SESSION_MS)

    private var returning = false
    private var routeFailures = 0
    private var emptySearches = 0
    private var escapeAttempts = 0

    constructor(bot: Bot, data: PkData) : this(bot, data.duration) {
        returning = data.returning
        routeFailures = data.routeFailures.coerceIn(0, MAX_ROUTE_FAILURES)
        emptySearches = data.emptySearches.coerceIn(0, MAX_EMPTY_SEARCHES)
        escapeAttempts = data.escapeAttempts.coerceIn(0, MAX_ESCAPE_ATTEMPTS)
    }

    override suspend fun init(resumed: Boolean): Boolean {
        if (!bot.isAlive || bot.state != EntityState.ACTIVE) return true
        if (isExpired() || returning) {
            returning = true
            return false
        }
        // A restored or interrupted session already in the Wilderness must not bank through a travel fallback.
        if (bot.inWilderness()) {
            returning = !hasCombatSupplies()
            return false
        }
        if (!isEligible(bot) || bot.combat.inCombat()) return true
        val prepared = withTimeoutOrNull(120_000) { prepareSupplies() } == true
        if (!prepared) bot.log("PK preparation failed or timed out; ending session.")
        return !prepared
    }

    override fun paused() {
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() {
        // Combat belongs to the child script after a PK pause, not to this cleanup hook.
        if (!isPaused()) {
            bot.navigator.cancel()
            bot.walking.clear()
            bot.combat.target = null
        }
    }

    override suspend fun run(): Boolean {
        if (!bot.isAlive || bot.state != EntityState.ACTIVE) return true
        if (isExpired() || !hasCombatSupplies() || bot.healthPercent < 30) returning = true
        if (returning) return returnHome()
        if (checkCombat()) return false
        if (!enterWild()) {
            routeFailures++
            if (routeFailures >= MAX_ROUTE_FAILURES) returning = true
            delay(3.seconds)
            return false
        }
        if (!searchAndAttack()) {
            emptySearches++
            if (emptySearches >= MAX_EMPTY_SEARCHES) returning = true
            delay(3.seconds)
        }
        // pushHead cancels this coroutine; the parent must remain resumable after the fight.
        return false
    }

    override fun snapshot(): PkData = PkData().also {
        it.duration = (expireAt - System.currentTimeMillis()).coerceIn(0, MAX_SESSION_MS).milliseconds
        it.returning = returning
        it.routeFailures = routeFailures
        it.emptySearches = emptySearches
        it.escapeAttempts = escapeAttempts
    }

    /** Whether this session may still initiate or continue a fight. */
    internal fun canHunt(): Boolean = !returning && !isExpired() && bot.isAlive &&
        bot.state == EntityState.ACTIVE && hasCombatSupplies() && bot.healthPercent >= 30

    internal fun isExpired(): Boolean = System.currentTimeMillis() >= expireAt

    /** Uses the existing controller and single/multi-combat rules in addition to Wilderness membership. */
    internal fun isValidTarget(other: Player): Boolean = other !== bot && bot.inWilderness() &&
        other.inWilderness() && other.state == EntityState.ACTIVE && other.isAlive &&
        other.position.z == bot.position.z && other.isViewableFrom(bot) &&
        bot.combat.isAttackable && other.combat.isAttackable &&
        bot.controllers.checkCombat(other) && bot.combat.checkMultiCombat(other)

    /** Defends against a valid player, or leaves when the current encounter is unsuitable for this PK session. */
    suspend fun checkCombat(): Boolean {
        if (!bot.combat.inCombat()) return false
        val attacker = bot.combat.lastCombatWith
        if (attacker is Player && canHunt() && isValidTarget(attacker)) {
            bot.scriptStack.pushHead(CombatBotScript(bot, attacker, pkSession = this))
        } else {
            returning = true
        }
        return true
    }

    /** Queues one combat child; actual attacks use the ordinary player interaction packet. */
    fun searchAndAttack(): Boolean {
        if (!canHunt() || bot.combat.inCombat() || !bot.inWilderness()) return false
        val targets = world.locator.findViewablePlayers(bot).filter { isValidTarget(it) }
            .sortedWith(LocatableDistanceComparator(bot))
        val target = targets.firstOrNull() ?: return false
        emptySearches = 0
        bot.scriptStack.pushHead(CombatBotScript(bot, target, pkSession = this))
        return true
    }

    /** Reports failed engagement so an unreachable target cannot restart combat indefinitely. */
    internal fun failedEngagement() {
        routeFailures++
        if (routeFailures >= MAX_ROUTE_FAILURES) returning = true
    }

    private fun hasCombatSupplies(): Boolean = hasMeleeEquipment(bot) && foodCount(bot.inventory) >= 2

    /** Deposits carried items and withdraws owned food without generating equipment or supplies. */
    private suspend fun prepareSupplies(): Boolean {
        if (!handler.banking.travelToBankDepositAll()) return false
        handler.banking.clickBankingMode(false)
        if (!handler.banking.withdrawAnyFood(START_FOOD, MINIMUM_HEAL, retry = false)) return false
        return foodCount(bot.inventory) >= START_FOOD && hasMeleeEquipment(bot) &&
            handler.widgets.clickCloseInterface()
    }

    private suspend fun enterWild(): Boolean {
        if (bot.inWilderness()) return true
        val anchor = PkArea.LOW_LEVEL.anchors.filter { it.y >= 3520 }.minBy { it.computeLongestDistance(bot.position) }
        bot.walking.isRunning = true
        return navigate(anchor, interruptForCombat = true) && bot.inWilderness()
    }

    /** Cancels both the navigation request and queued walking when a bounded route times out. */
    private suspend fun navigate(position: Position, interruptForCombat: Boolean = false): Boolean {
        val reached = withTimeoutOrNull(45_000) {
            val pending = bot.navigator.navigate(position, true)
            while (!pending.isDone && !(interruptForCombat && bot.combat.inCombat())) delay(600.milliseconds)
            pending.isDone && !pending.isCancelled && pending.await() == NavigationResult.REACHED
        } == true
        if (!reached) {
            bot.navigator.cancel()
            bot.walking.clear()
        }
        return reached
    }

    /** Attempts real walking/teleport actions only; no forced travel fallback bypasses Wilderness restrictions. */
    private suspend fun returnHome(): Boolean {
        bot.combat.target = null
        if (!bot.inWilderness()) return true
        if (escapeAttempts >= MAX_ESCAPE_ATTEMPTS) {
            bot.log("PK escape budget exhausted; ending session for normal reflex handling.")
            return true
        }
        escapeAttempts++
        val escaped = withTimeoutOrNull(60_000) {
            // Walking out also handles teleblock; these anchors are south of the Wilderness boundary.
            val outside = listOf(Position(3092, 3517), Position(3194, 3517), Position(3274, 3517))
                .minBy { it.computeLongestDistance(bot.position) }
            if (!navigate(outside) || bot.inWilderness()) return@withTimeoutOrNull false
            output.sendCommand("home")
            waitFor(10.seconds) { bot.subZone == SubZone.HOME }
        } == true
        if (!escaped) {
            bot.navigator.cancel()
            bot.walking.clear()
            delay(3.seconds)
        }
        return escaped || !bot.inWilderness()
    }
}
