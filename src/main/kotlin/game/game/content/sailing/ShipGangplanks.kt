package game.content.sailing

import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * The gangplanks between the docks and the decks of the ships, and where crossing them takes a player.
 *
 * @author Hydrozoa
 */
object ShipGangplanks {

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
    val LEAVING_PLANKS = setOf(2082, 2084, 2086, 2088, 2413, 2415)

    /**
     * The Entrana ships' gangplanks, which don't say "You board the ship."
     */
    val ENTRANA_PLANKS = setOf(2412, 2414)

    /**
     * The step, along x and y, that goes from the dock onto the deck over [plank].
     */
    private fun towardsDeck(plank: GameObject): Pair<Int, Int> =
        // A plank's direction points from the deck to the dock.
        when (plank.direction) {
            ObjectDirection.WEST -> 1 to 0
            ObjectDirection.EAST -> -1 to 0
            ObjectDirection.NORTH -> 0 to -1
            ObjectDirection.SOUTH -> 0 to 1
        }

    /**
     * The tile a player crossing [plank] from plane [z] is first moved to, which is the dock end of the gangplank. They go
     * up onto the deck if [aboard], or down onto the dock if not.
     */
    fun step(plank: GameObject, z: Int, aboard: Boolean): Position {
        val (dx, dy) = towardsDeck(plank)
        // The dock end is a bridge tile, so it's only drawn on the plank at the dock's height.
        return if (aboard) Position(plank.position.x, plank.position.y, z)
               else Position(plank.position.x - dx, plank.position.y - dy, z - 1)
    }

    /**
     * The tile a player crossing [plank] from plane [z] lands on, a tick after the [step]: on the deck if [aboard], or on
     * the dock if not.
     */
    fun landing(plank: GameObject, z: Int, aboard: Boolean): Position {
        val (dx, dy) = towardsDeck(plank)
        val way = if (aboard) 1 else -1
        return Position(plank.position.x + 2 * dx * way, plank.position.y + 2 * dy * way, z + way)
    }
}
