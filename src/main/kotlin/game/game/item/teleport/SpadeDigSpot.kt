package game.item.teleport

import io.luna.game.model.Position
import io.luna.game.model.area.Area

/**
 * The places where digging with a spade breaks through the ground: the six Barrows mounds, which lead into their
 * brother's crypt, and the Falador Park mole hills, which lead into the Giant Mole's lair.
 *
 * @property landing Where the player lands after falling through.
 * @property areas Where the player has to stand, on the ground floor, to dig through.
 * @property arrival The message sent once the player lands, if any.
 * @property dark Whether it's too dark below to go down without a lit light.
 * @author TheLining
 */
enum class SpadeDigSpot(val landing: Position, private vararg val areas: Area, val arrival: String? = null,
                        val dark: Boolean = false) {
    AHRIM_MOUND(Position(3557, 9703, 3), Area.of(3562, 3286, 3567, 3292)),
    DHAROK_MOUND(Position(3556, 9718, 3), Area.of(3572, 3295, 3577, 3300)),
    GUTHAN_MOUND(Position(3534, 9704, 3), Area.of(3574, 3280, 3580, 3284)),
    KARIL_MOUND(Position(3546, 9684, 3), Area.of(3563, 3273, 3569, 3279)),
    TORAG_MOUND(Position(3568, 9683, 3), Area.of(3552, 3281, 3556, 3284)),
    VERAC_MOUND(Position(3578, 9706, 3), Area.of(3555, 3295, 3559, 3300)),

    // Every mole hill comes out at the same spot, under the hole in the lair's roof.
    FALADOR_MOLE_HILLS(Position(1752, 5237), tile(2984, 3387), tile(2987, 3387), tile(2989, 3378),
                       tile(2996, 3377), tile(2999, 3375), tile(3005, 3376),
                       arrival = "You seem to have dropped down into a network of mole tunnels.", dark = true);

    companion object {

        /**
         * Returns the spot that digging on [position] breaks through, or `null` if there's nothing to find there.
         */
        fun forPosition(position: Position): SpadeDigSpot? =
            if (position.z != 0) null else entries.firstOrNull { spot -> spot.areas.any { it.contains(position) } }
    }
}

/**
 * Returns an area covering only the tile at [x] and [y].
 */
private fun tile(x: Int, y: Int) = Area.of(x, y, x, y)
