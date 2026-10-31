package game.skill.agility

import io.luna.game.model.mob.block.PlayerModelAnimation

/**
 * The stand and turn animations that go with each obstacle walk. The client only takes up new stand and walk
 * animations while the player walks normally, not during exact movement, so legs with a walking animation are walked
 * a tile a tick with all of them.
 *
 * @param walk The walking animation, used for every direction.
 * @param ready The animation while standing still on the obstacle.
 * @param turn The animation while turning on the spot.
 * @author TheLining
 */
enum class AgilityWalk(val walk: Int, private val ready: Int, private val turn: Int = walk) {

    /**
     * HUMAN_WALK_LOGBALANCE with HUMAN_WALK_LOGBALANCE_READY: logs, tightropes and the ridge.
     */
    BALANCE(762, 763),

    /**
     * HUMAN_WALK_SIDESTEP with HUMAN_READY_SIDESTEP.
     */
    SIDESTEP(754, 755, 755),

    /**
     * HUMAN_WALK_SIDESTEPL with HUMAN_READY_SIDESTEPL.
     */
    SIDESTEP_LEFT(756, 757, 757),

    /**
     * HUMAN_CLIMBING with HUMAN_CLIMBING_READY: the Wilderness rocks.
     */
    CLIMB(737, 738),

    /**
     * HUMAN_MONKEYBARS_WALK with HUMAN_MONKEYBARS_READY.
     */
    MONKEY_BARS(744, 745);

    /**
     * The full set of animations for this walk.
     */
    val model: PlayerModelAnimation = walkModel(ready, turn, walk)

    companion object {

        /**
         * The ordinary walk. Legs that use it keep the player's own stand and walk animations.
         */
        const val NORMAL = 819

        /**
         * Returns the animations to show for [walk], or [own] for the ordinary walk and for legs without one. A walk
         * not listed here is also used to stand and turn.
         */
        fun modelFor(walk: Int, own: PlayerModelAnimation?): PlayerModelAnimation? = when (walk) {
            -1, NORMAL -> own
            else -> values().firstOrNull { it.walk == walk }?.model ?: walkModel(walk, walk, walk)
        }
    }
}

/**
 * Builds a set of animations that uses [walk] for every walking direction. Kept outside [AgilityWalk] because its
 * entries are built before its companion object.
 */
private fun walkModel(ready: Int, turn: Int, walk: Int) = PlayerModelAnimation.Builder()
    .setStandingId(ready)
    .setStandingTurnId(turn)
    .setWalkingId(walk)
    .setTurning180DegreesId(walk)
    .setTurning90DegreesCwId(walk)
    .setTurning90DegreesCcwId(walk)
    .setRunningId(walk)
    .build()
