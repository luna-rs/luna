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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Wilderness PK bot behaviour script.
 *
 * This script sends a bot into the Wilderness, moves it through configured PK hotspots, searches for valid player
 * targets, and reacts to combat encounters. Once the configured [duration] expires, the bot attempts to flee the
 * Wilderness and finish safely.
 *
 * Movement is currently based on manually verified anchor points. Future versions should move more of this into the
 * zone/area system so bots can recognize caves, gates, one-way routes, high-risk paths, and special escape cases.
 *
 * @param bot The bot controlled by this script.
 * @param duration The maximum amount of time this script should actively PK before fleeing.
 */
class PkBotScript(bot: Bot, val duration: Duration) : BotScript<Duration>(bot) {

    // TODO@1.0 Generate Wilderness area metadata from verified region ids.
    //  Anchor points can be seeded from each region's base position plus a few verified walkable tiles. Low-level
    //  anchors should come from the lower Wilderness rows, while deep Wilderness anchors should bias toward gates,
    //  exits, and safer transition points.
    //
    //  Here is a grid of Wilderness region ids in the same layout as the actual OSRS map:
    //
    //  11837, 12093, 12349, 12605, 12861, 13117, 13373
    //  11836, 12092, 12348, 12604, 12860, 13116, 13372
    //  11835, 12091, 12347, 12603, 12859, 13115, 13371
    //  11834, 12090, 12346, 12602, 12858, 13114, 13370
    //  11833, 12089, 12345, 12601, 12857, 13113, 13369
    //  11832, 12088, 12344, 12600, 12856, 13112, 13368
    //  11831, 12087, 12343, 12599, 12855, 13111, 13367
    //
    //  The last two bottom rows can be used for low-level anchors, while the top two rows represent the highest
    //  Wilderness areas.

    // TODO@1.0 Separate deep Wilderness from normal Wilderness through the zoning system. If the bot is in a
    //  DEEP_WILD zone and not inside a cave or special instance, anchor points should prefer access gates,
    //  area exits, and other verified transition points.

    /**
     * Shared PK bot constants.
     */
    companion object {

        /**
         * Low-level Wilderness anchor points used for entering, escaping, and returning toward safer Wilderness levels.
         *
         * These are spread across the lower Wilderness so bots do not always enter or escape through the exact same
         * tile. They are currently hardcoded, but should eventually be generated from verified Wilderness regions and
         * area metadata.
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

        /**
         * Immutable list of all configured PK areas.
         */
        companion object {
            val ALL = ImmutableList.copyOf(values())
        }
    }

    /**
     * Absolute script expiry time in nanoseconds.
     *
     * Once this time is reached, the bot stops hunting and attempts to flee the Wilderness.
     */
    var expireAt: Long = System.nanoTime() + duration.toLong(DurationUnit.NANOSECONDS)

    override suspend fun run(): Boolean {
        if (System.nanoTime() > expireAt) {
            bot.log("Script expired. Travelling back home.")

            val finished = handler.travelHome()
            bot.log("travelHome finished=$finished, inWilderness=${bot.inWilderness()}")

            delay(1.seconds, 3.seconds)
            return finished
        }

        if (checkCombat()) {
            bot.log("checkCombat handled current state.")
            delay(1.seconds, 3.seconds)
            return false
        }

        if (!resetItems()) {
            bot.log("resetItems returned false.")
            delay(1.seconds, 3.seconds)
            return false
        }

        if (!enterWild()) {
            bot.log("enterWild returned false.")
            delay(1.seconds, 3.seconds)
            return false
        }

        travelToNewArea()

        if (!searchAndAttack()) {
            bot.log("No target found. Delaying before retry.")
            delay(1.seconds, 3.seconds)
            return false
        }

        bot.log("Target found. Combat script should now be active.")
        return bot.combat.inCombat()
    }

    override fun snapshot(): Duration {
        val remaining = expireAt - System.nanoTime()
        if (remaining <= 0) {
            return Duration.ZERO
        }
        return remaining.nanoseconds
    }

    /**
     * Handles the bot's current combat state.
     *
     * If the bot is attacked outside the Wilderness, it immediately teleports home. Wilderness NPC combat may cause the
     * bot to flee depending on NPC strength and randomness. Wilderness player combat pushes a dedicated
     * [CombatBotScript] so the bot can fight back using combat-specific behaviour.
     *
     * @return `true` if combat state was handled this tick, otherwise `false`.
     */
    suspend fun checkCombat(): Boolean {
        val attacker = bot.combat.lastCombatWith

        if (bot.combat.inCombat() && attacker != null) {
            bot.log(
                "In combat. attacker=${attacker.javaClass.simpleName}, " +
                        "inWilderness=${bot.inWilderness()}, attacker=$attacker"
            )

            if (!bot.inWilderness()) {
                bot.log("Bot is in combat outside wilderness. Sending home command.")
                output.sendCommand("home")
                return true
            } else if (attacker is Npc) {
                val shouldFlee =
                    attacker.def().combatLevel * 2 > bot.combatLevel || rand(1 of 3) // TODO@1.0 Base on intelligence.
                bot.log(
                    "Npc attacker=${attacker.id}, npcLevel=${attacker.def().combatLevel}, " +
                            "botLevel=${bot.combatLevel}, shouldFlee=$shouldFlee"
                )

                if (shouldFlee) {
                    bot.log("Fleeing NPC combat.")
                    handler.combat.fleeCombat()
                } else {
                    bot.log("NPC attacker detected, but bot chose not to flee this tick.")
                    bot.scriptStack.pushHead(CombatBotScript(bot, attacker))
                }

                return true
            } else if (attacker is Player) {
                bot.log("Player attacker detected. Pushing CombatBotScript for $attacker")
                bot.scriptStack.pushHead(CombatBotScript(bot, attacker))
                return true
            }
        }

        return false
    }

    /**
     * Searches nearby viewable players and attacks the first suitable target.
     *
     * Candidate targets must be alive, active, and valid according to multi-combat rules. If multiple candidates are
     * found, the bot may sort them by distance or health to create less predictable target selection.
     *
     * @return `true` if a target was selected and attacked, otherwise `false`.
     */
    fun searchAndAttack(): Boolean {

        /**
         * Checks whether [other] is a valid target candidate for this bot.
         */
        fun check(other: Player): Boolean {
            return other.isAlive &&
                    bot.combat.checkMultiCombat(other) &&
                    other.state == EntityState.ACTIVE
        }

        if (bot.combat.inCombat()) {
            bot.log("Skipping target search because bot is already in combat.")
            return false
        }

        if (!bot.inWilderness()) {
            bot.log("Skipping target search because bot is not in wilderness.")
            return false
        }

        val targets = ArrayList<Player>()
        for (other in world.locator.findViewablePlayers(bot)) {
            if (other != bot && check(other)) {
                targets += other
            }
        }

        bot.log("Found ${targets.size} candidate targets in view.")

        if (targets.size > 1) {
            if (rand(1 of 3)) {
                bot.log("Sorting candidate targets by distance.")
                targets.sortWith(LocatableDistanceComparator(bot))
            } else if (rand(1 of 4)) {
                bot.log("Sorting candidate targets by health percent.")
                targets.sortBy { it.healthPercent }
            } else {
                bot.log("Leaving candidate targets unsorted.")
            }
        }

        for (other in targets) {
            if (check(other)) {
                bot.log("Selected target $other. Pushing CombatBotScript.")
                bot.combat.attack(other)
                bot.scriptStack.pushHead(CombatBotScript(bot, other))
                return true
            }
        }

        bot.log("No valid target selected after filtering.")
        return false
    }

    /**
     * Occasionally sends the bot to a random configured PK area.
     *
     * The bot only travels when it is in the Wilderness, not already navigating, not in combat, and has enough tolerance
     * remaining to justify moving to another hotspot.
     */
    private suspend fun travelToNewArea() {
        if (!bot.combat.inCombat() &&
            bot.inWilderness() &&
            !bot.navigator.isActive &&
            bot.tolerance.duration.toMinutes() > 10) {
            val area = PkArea.ALL.random()
            val anchor = area.anchors.random()

            bot.log("Travelling to new area ${area.name}, anchor=$anchor")
            bot.navigator.navigate(anchor, true).await()
        }
    }

    /**
     * Verifies and resets the bot's PK supplies.
     *
     * This is currently a placeholder. Future logic should verify food, runes, ammunition, teleport options, combat
     * gear, and other resources required by the bot's combat profile.
     *
     * @return `true` if the bot is ready to continue PKing, otherwise `false`.
     */
    private fun resetItems(): Boolean {
        bot.log("resetItems called.")
        // TODO@0.5.0 Make sure we have food and proper combat equipment.
        return true
    }

    /**
     * Ensures the bot is inside the Wilderness before searching for targets.
     *
     * If the bot is outside the Wilderness, it navigates to a random low-level Wilderness anchor and verifies that the
     * destination actually placed it inside the Wilderness.
     *
     * @return `true` if the bot is in the Wilderness, otherwise `false`.
     */
    private suspend fun enterWild(): Boolean {
        if (!bot.inWilderness()) {
            val anchor = LOW_LEVEL_ANCHOR_POINTS.random()

            bot.log("Bot is outside wilderness. Walking to low level anchor $anchor")
            handler.widgets.clickRunning(true)
            bot.navigator.navigate(anchor, true).await()

            if (!bot.inWilderness()) {
                bot.log("Reached anchor but bot is still not in wilderness. position=${bot.position}")
                return false
            }

            bot.log("Bot entered wilderness successfully at position=${bot.position}")
        }
        return true
    }
}