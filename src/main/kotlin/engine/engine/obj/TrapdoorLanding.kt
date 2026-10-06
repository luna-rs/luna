package engine.obj

import io.luna.game.model.Direction
import io.luna.game.model.Position

/**
 * Where climbing down a trapdoor takes a player.
 *
 * @author TheLining
 */
fun interface TrapdoorLanding {

    /**
     * Returns where a player standing on [player] lands after climbing down the trapdoor on [trapdoor], or `null` if
     * that's off the map's height levels.
     */
    fun from(trapdoor: Position, player: Position): Position?

    companion object {

        /**
         * Lands in the cellar under the player.
         */
        val BELOW = TrapdoorLanding { _, player -> player.translate(0, LadderType.CELLAR_OFFSET) }

        /**
         * Always lands on the same tile.
         */
        fun tile(x: Int, y: Int, z: Int = 0) = TrapdoorLanding { _, _ -> Position(x, y, z) }

        /**
         * Lands [floors] floors under the player.
         */
        fun floorsDown(floors: Int) = TrapdoorLanding { _, player ->
            val z = player.z - floors
            if (Position.HEIGHT_LEVELS.contains(z)) Position(player.x, player.y, z) else null
        }

        /**
         * Lands using [landings] for the trapdoors on those positions, and [otherwise] for the rest.
         */
        fun byTrapdoor(otherwise: TrapdoorLanding, vararg landings: Pair<Position, TrapdoorLanding>): TrapdoorLanding {
            val byPosition = landings.toMap()
            return TrapdoorLanding { trapdoor, player -> (byPosition[trapdoor] ?: otherwise).from(trapdoor, player) }
        }

        /**
         * Returns where a player heading for [target] lands: on [target] itself, or if it has no floor or can't be
         * walked onto (the ladder back up is often there), on the closest tile beside it that can. `null` if there's
         * no such tile.
         */
        fun land(target: Position): Position? =
            if (LadderDestination.canLand(target)) target else
                Direction.NESW.map { target.translate(1, it) }.firstOrNull { LadderDestination.canLand(it) }
    }
}
