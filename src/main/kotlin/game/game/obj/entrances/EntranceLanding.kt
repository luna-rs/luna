package game.obj.entrances

import engine.obj.TrapdoorLanding
import io.luna.game.model.Position

/**
 * Where using an [Entrance] takes a player.
 *
 * @author TheLining
 */
fun interface EntranceLanding {

    /**
     * Returns where a player standing on [player] lands.
     */
    fun from(player: Position): Position

    companion object {

        /**
         * Always lands on the same tile.
         */
        fun tile(x: Int, y: Int, z: Int = 0) = EntranceLanding { Position(x, y, z) }

        /**
         * Lands on the tile, or beside it if it can't be stood on.
         */
        fun near(x: Int, y: Int, z: Int = 0): EntranceLanding {
            val target = Position(x, y, z)
            return EntranceLanding { TrapdoorLanding.land(target) ?: target }
        }

        /**
         * Lands [x], [y] and [z] away from where the player stood.
         */
        fun shift(x: Int, y: Int, z: Int = 0) = EntranceLanding { it.translate(x, y, z) }

        /**
         * Keeps the player's x and lands on [y] and [z].
         */
        fun row(y: Int, z: Int = 0) = EntranceLanding { Position(it.x, y, z) }

        /**
         * Keeps the player's y and crosses to the other side of the line between [west] and [east]: players west of
         * it land on [east], and the rest on [west].
         */
        fun across(west: Int, east: Int, z: Int = 0) =
            EntranceLanding { Position(if (it.x <= west) east else west, it.y, z) }

        /**
         * Keeps the player's x and crosses to the other side of the line between [south] and [north].
         */
        fun acrossNorth(south: Int, north: Int, z: Int = 0) =
            EntranceLanding { Position(it.x, if (it.y <= south) north else south, z) }
    }
}
