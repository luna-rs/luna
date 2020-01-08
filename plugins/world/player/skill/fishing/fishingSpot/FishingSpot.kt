package world.player.skill.fishing.fishingSpot

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc

/**
 * A model representing a fishing spot.
 */
class FishingSpot(id: Int,
                  val home: Position,
                  val away: Position) : Npc(ctx, id, home) {

    companion object {

        /**
         * A range of how often fishing spots can move, in minutes.
         */
        val MOVE_INTERVAL = 1..7 // 1-7 minutes.
    }

    /**
     * The countdown timer. This spot will move when it reaches 0.
     */
    var countdown = MOVE_INTERVAL.random()

    /**
     * Performs a countdown and returns true if the spot should be moved.
     */
    fun countdown(): Boolean {
        countdown--
        if (countdown <= 0) {
            countdown = MOVE_INTERVAL.random()
            return true
        }
        return false
    }
}