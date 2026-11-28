package game.content.merlinsCrystal

import game.obj.entrances.Entrances
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.`object`.GameObject

/**
 * Arhein's original crate voyage between Catherby and Keep Le Faye.
 *
 * @author lare96
 */
object MerlinVoyage {
    const val CATHERBY_CRATE = 63
    const val KEEP_CRATE = 64
    val catherbyCrate = Position(2801, 3442)
    val keepCrate = Position(2779, 3401, 1)
    val catherbyArrival = Position(2802, 3441)
    val keepArrival = Position(2778, 3401)

    fun offer(player: Player, crate: GameObject, fromCatherby: Boolean) {
        val origin = if (fromCatherby) catherbyCrate else keepCrate
        val destination = if (fromCatherby) keepArrival else catherbyArrival
        if (crate.position != origin) return
        player.newDialogue().options(MerlinsCrystal.HIDE_IN_CRATE, { passenger ->
            if (crate.state == EntityState.ACTIVE && passenger.isAlive && !passenger.isLocked &&
                passenger.world.collisionManager.reached(passenger, crate, InteractionPolicy.STANDARD_SIZE)) {
                passenger.overlays.closeWindows(false)
                Entrances.travel(passenger, destination, "You climb inside the crate and wait.", delay = 4,
                    arrival = "The crew unloads the crate and you climb out.", animation = 827)
            }
        }, "Stay here.", {}).open()
    }
}
