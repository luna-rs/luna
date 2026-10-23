package game.item.teleport

import io.luna.game.model.Position
import io.luna.game.model.area.Area

/**
 * The places where digging with a spade breaks through the ground: the six Barrows mounds, which lead into their
 * brother's crypt.
 *
 * @property landing Where the player lands after falling through.
 * @property area Where the player has to stand, on the ground floor, to dig through.
 * @author TheLining
 */
enum class SpadeDigSpot(val landing: Position, private val area: Area) {
    AHRIM_MOUND(Position(3557, 9703, 3), Area.of(3562, 3286, 3567, 3292)),
    DHAROK_MOUND(Position(3556, 9718, 3), Area.of(3572, 3295, 3577, 3300)),
    GUTHAN_MOUND(Position(3534, 9704, 3), Area.of(3574, 3280, 3580, 3284)),
    KARIL_MOUND(Position(3546, 9684, 3), Area.of(3563, 3273, 3569, 3279)),
    TORAG_MOUND(Position(3568, 9683, 3), Area.of(3552, 3281, 3556, 3284)),
    VERAC_MOUND(Position(3578, 9706, 3), Area.of(3555, 3295, 3559, 3300));

    companion object {

        /**
         * Returns the spot that digging on [position] breaks through, or `null` if there's nothing to find there.
         */
        fun forPosition(position: Position): SpadeDigSpot? =
            if (position.z != 0) null else entries.firstOrNull { it.area.contains(position) }
    }
}
