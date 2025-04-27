package world.player.skill.fishing.catchFish

import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Npc

/**
 * A [RepeatingAction] that makes a [FishingSpot] type move back and forth randomly between a set of positions.
 */
class MoveFishingSpotAction(private val spot: FishingSpot) : Action<Npc>(spot, ActionType.SOFT, false, 100) {

    companion object {

        /**
         * A range of how often fishing spots can move, in minutes.
         */
        val MOVE_INTERVAL = 1..7 // 1-7 minutes.
    }

    /**
     * The countdown before the fishing spot will move.
     */
    private var countdown = MOVE_INTERVAL.random()

    override fun run(): Boolean {
        if (--countdown <= 0) {
            when (spot.position) {
                spot.home -> spot.move(spot.away.random())
                else -> spot.move(spot.home)
            }
            countdown = MOVE_INTERVAL.random()
        }
        return false
    }
}