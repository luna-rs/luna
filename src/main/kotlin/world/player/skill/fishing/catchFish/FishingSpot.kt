package world.player.skill.fishing.catchFish

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc

/**
 * A model representing a fishing spot.
 */
class FishingSpot(id: Int,
                  val home: Position,
                  val away: List<Position>) : Npc(ctx, id, home) {

    override fun onActive() {
        submitAction(MoveFishingSpotAction(this))
    }
}