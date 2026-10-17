package game.content.sailing

import api.predef.*
import api.predef.ext.*
import game.content.sailing.ShipGangplanks.BOARDING_PLANKS
import game.content.sailing.ShipGangplanks.ENTRANA_PLANKS
import game.content.sailing.ShipGangplanks.LEAVING_PLANKS
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * Moves [plr] across [plank], up onto the deck if [aboard] or down onto the dock if not. They step onto the dock end
 * of the gangplank, then land on the deck or dock a tick later.
 */
fun cross(plr: Player, plank: GameObject, aboard: Boolean) {
    val step = ShipGangplanks.step(plank, plr.z, aboard)
    val landing = ShipGangplanks.landing(plank, plr.z, aboard)
    plr.lock()
    plr.move(step)
    world.scheduleOnce(1) {
        plr.move(landing)
        plr.unlock()
    }
}

for ((id, message) in BOARDING_PLANKS) {
    object1(id) {
        cross(plr, gameObject, true)
        if (id !in ENTRANA_PLANKS) {
            plr.sendMessage("You board the ship.")
        }
        if (message != null) {
            plr.sendMessage(message)
        }
    }
}

for (id in LEAVING_PLANKS) {
    object1(id) { cross(plr, gameObject, false) }
}
