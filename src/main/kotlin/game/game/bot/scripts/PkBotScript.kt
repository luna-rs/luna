package game.bot.scripts

import api.bot.BotScript
import api.bot.Suspendable.delay
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.EntityState
import io.luna.game.model.LocatableDistanceComparator
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.future.await
import java.time.Duration
import java.util.*
import kotlin.time.Duration.Companion.seconds

// todo use kotlin duration
class PkBotScript(bot: Bot, val duration: Duration) : BotScript<Duration>(bot) {

    companion object {
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
    }

    // common areas that the bot will tend to wander/route through
    // bot will wander amongst anchor points, or just sometimes randomly dumb wander
    enum class PkArea(val regions: Set<Int>, val anchors: List<Position>) {
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

        companion object {
            val ALL = ImmutableList.copyOf(values())
        }
        // TODO Deep wilderness, after doors and gates implementation.
    }
    // locates targets in the wild and starts fights
    // DO NOT look for players globally... make the bots dumb or smart wander within the wilderness area and/or
    // go to specialized hotspots (green drags, kbd, rune rocks etc. and alter through that area) while travelling in the wild if a local player appears in its
    // updating view? then try to attack the target. maybe use patrolaction?

    // ensure we can even attack before targeting... check multi, wild level, etc.
    var expireAt: Long = System.nanoTime() + duration.toNanos()
    suspend fun checkCombat(): Boolean {
        // TODO If health under 50% and no food, leave wilderness.
        val attacker = bot.combat.lastCombatWith
        if (bot.combat.inCombat() && attacker != null) {
            if (!bot.inWilderness()) {
                // We're not in the wilderness, teleport away from the threat.
                output.sendCommand("home")
                return true
            } else if (attacker is Npc) {
                if (attacker.def().combatLevel * 2 > bot.combatLevel || rand(1 of 3)) {
                    // We're in the wild, run away from strong NPCs (or when we don't feel like fighting).
                    handler.combat.fleeCombat()
                }
                return true
            } else if(attacker is Player) {
                // We were attacked by a player, fight back.
                bot.scriptStack.pushHead(CombatBotScript(bot, attacker))
                return true
            }
        }
        return false
    }

    fun searchAndAttack(): Boolean {
        fun check(other: Player) =
            other.isAlive && bot.combat.checkMultiCombat(other) && other.state == EntityState.ACTIVE

        val targets = ArrayList<Player>()
        if (!bot.combat.inCombat() && bot.inWilderness()) {
            for (other in world.locator.findViewablePlayers(bot)) {
                if (other != bot && check(other)) {
                    targets += other
                }
            }
        }
        if (targets.size > 1) {
            // TODO Intelligent bots much more likely to sort the following ways.
            if (rand(1 of 3)) {
                // Sort targets by distance sometimes.
                Collections.sort(targets, LocatableDistanceComparator(bot))
            } else if (rand(1 of 4)) {
                // Sort targets by health percentage sometimes.
                targets.sortWith { o1, o2 -> o1.healthPercent.compareTo(o2.healthPercent) }
            }
        }
        for (other in targets) {
            if (other.isAlive && bot.combat.checkMultiCombat(other) && other.state == EntityState.ACTIVE) {
                bot.scriptStack.pushHead(CombatBotScript(bot, other))
                return true
            }
        }
        return false
    }

    fun travelToNewArea() {
        if (!bot.combat.inCombat() && bot.inWilderness() && !bot.movementStack.isActive && bot.tolerance.duration.toMinutes() > 10) {
            bot.movementStack.walkUntilReached(PkArea.ALL.random().anchors.random())
        }
    }

    fun resetItems(): Boolean {
        // TODO Make sure we have food and proper combat equipment.
        return true
    }

    suspend fun enterWild(): Boolean {
        if (!bot.inWilderness()) {
            handler.widgets.clickRunning(true)
            bot.movementStack.walkUntilReached(LOW_LEVEL_ANCHOR_POINTS.random()).await()
            if (!bot.inWilderness()) {
                return false
            }
        }
        return true
    }

    override suspend fun run(): Boolean {
        if (System.nanoTime() > expireAt) {
            // Script duration has ended, leave wilderness and end script.
            handler.combat.fleeWilderness()
            return !bot.inWilderness()
        }
        if (checkCombat() || !resetItems() || !enterWild()) {
            // Script preparation functions.
            return false
        }
        // Main script loop.
        travelToNewArea()
        if(!searchAndAttack()) {
            delay(1.seconds, 3.seconds)
            return false
        } else {
            return true
        }
    }

    override fun snapshot(): Duration {
        val remaining = expireAt - System.nanoTime()
        if (remaining <= 0) {
            return Duration.ZERO
        }
        return Duration.ofNanos(remaining)
    }
}