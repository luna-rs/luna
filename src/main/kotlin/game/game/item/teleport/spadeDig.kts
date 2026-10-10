package game.item.teleport

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * The spade item id.
 */
val SPADE = 952

/**
 * The dig animation, played on every dig.
 */
val SPADE_DIG_ANIMATION = Animation(830)

/**
 * The ticks between digging through and landing below, which lets the dig animation finish first.
 */
val SPADE_FALL_DELAY = 2

/**
 * Digs with the spade, falling through [spot] once the dig animation has played, or finding nothing if [spot] is
 * `null`.
 */
fun Player.digWithSpade(spot: SpadeDigSpot?) {
    walking.clear()
    animation(SPADE_DIG_ANIMATION)
    if (spot == null) {
        sendMessage("Nothing interesting happens.")
        return
    }
    lock()
    world.scheduleOnce(SPADE_FALL_DELAY) {
        unlock()
        if (isAlive) {
            move(spot.landing)
        }
    }
}

// Every Dig goes through this one handler, because all listeners registered for the spade run.
item1(SPADE) {
    plr.digWithSpade(SpadeDigSpot.forPosition(plr.position))
}
