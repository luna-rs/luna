package game.content.sailing

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * Gangplanks on the dock that lead aboard, mapped to what crossing them says.
 */
val BOARDING_PLANKS = mapOf(
    2081 to "You must speak to the Customs Officer before it will set sail.", // Musa Point
    2083 to "You must speak to one of the sailors before it will set sail.", // Port Sarim, to Karamja
    2085 to "You must speak to Captain Barnaby before it will set sail.", // Ardougne
    2087 to "You must speak to the Customs Officer before it will set sail.", // Brimhaven
    2412 to null, // Port Sarim, to Entrana
    2414 to null // Entrana
)

/**
 * Gangplanks on deck that lead ashore.
 */
val LEAVING_PLANKS = listOf(2082, 2084, 2086, 2088, 2413, 2415)

/**
 * The Entrana ships' gangplanks, which don't say "You board the ship."
 */
val ENTRANA_PLANKS = setOf(2412, 2414)

/**
 * Moves [plr] across [plank], up onto the deck if [aboard] or down onto the dock if not. They step onto the dock end
 * of the gangplank, then land on the deck or dock a tick later.
 */
fun cross(plr: Player, plank: GameObject, aboard: Boolean) {
    // A plank's direction points from the deck to the dock.
    val (dx, dy) = when (plank.direction) {
        ObjectDirection.WEST -> 1 to 0
        ObjectDirection.EAST -> -1 to 0
        ObjectDirection.NORTH -> 0 to -1
        ObjectDirection.SOUTH -> 0 to 1
    }
    val way = if (aboard) 1 else -1
    val dockZ = if (aboard) plr.z else plr.z - 1
    // The dock end is a bridge tile, so it's only drawn on the plank at the dock's height.
    val step = if (aboard) Position(plank.position.x, plank.position.y, dockZ)
               else Position(plank.position.x - dx, plank.position.y - dy, dockZ)
    val landing = Position(plank.position.x + 2 * dx * way, plank.position.y + 2 * dy * way, plr.z + way)
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
