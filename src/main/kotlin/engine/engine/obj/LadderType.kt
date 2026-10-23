package engine.obj

import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject

/**
 * The ways a ladder can move a player, who keeps their own x and y while climbing.
 *
 * @param refusal The message for ladders that can't be climbed.
 *
 * @author TheLining
 */
enum class LadderType(val refusal: String? = null) {

    /**
     * Climbs up one floor.
     */
    UP {
        override fun move(from: Position, up: Boolean) = from.climb(dz = 1)
    },

    /**
     * Climbs down one floor.
     */
    DOWN {
        override fun move(from: Position, up: Boolean) = from.climb(dz = -1)
    },

    /**
     * Climbs up two floors.
     */
    UP_TWO_FLOORS {
        override fun move(from: Position, up: Boolean) = from.climb(dz = 2)
    },

    /**
     * Climbs down two floors.
     */
    DOWN_TWO_FLOORS {
        override fun move(from: Position, up: Boolean) = from.climb(dz = -2)
    },

    /**
     * Climbs up or down one floor.
     */
    UP_OR_DOWN {
        override fun move(from: Position, up: Boolean) = from.climb(dz = if (up) 1 else -1)
    },

    /**
     * Climbs down into the dungeon below.
     */
    INTO_CELLAR {
        override fun move(from: Position, up: Boolean) = from.climb(dy = CELLAR_OFFSET)
    },

    /**
     * Climbs up out of a dungeon.
     */
    OUT_OF_CELLAR {
        override fun move(from: Position, up: Boolean) = from.climb(dy = -CELLAR_OFFSET)
    },

    /**
     * Can't be climbed.
     */
    BROKEN(refusal = "The ladder is broken, I can't climb it.") {
        override fun move(from: Position, up: Boolean): Position? = null
    },

    /**
     * Leads nowhere, so the player won't climb it.
     */
    UNTRUSTED(refusal = "Hmmm... I don't trust that ladder. I'm not going to climb down it.") {
        override fun move(from: Position, up: Boolean): Position? = null
    },

    /**
     * Does nothing.
     */
    NOTHING(refusal = "Nothing interesting happens.") {
        override fun move(from: Position, up: Boolean): Position? = null
    };

    /**
     * Returns where a player standing on [from] ends up, or `null` if this type can't be climbed.
     *
     * @param from The tile the player climbs from.
     * @param up `true` if the player chose to climb up. Only used by [UP_OR_DOWN].
     */
    abstract fun move(from: Position, up: Boolean): Position?

    /**
     * Returns this tile moved by [dy] and [dz], or `null` if that leaves the map's height levels.
     */
    protected fun Position.climb(dy: Int = 0, dz: Int = 0): Position? {
        val newZ = z + dz
        return if (Position.HEIGHT_LEVELS.contains(newZ)) Position(x, y + dy, newZ) else null
    }

    companion object {

        /**
         * How far underground areas are from the surface above them.
         */
        const val CELLAR_OFFSET = 6400

        /**
         * Ladders whose menu actions and position don't give their type (see [forObject]).
         */
        private val TYPES = mapOf(
            287 to UNTRUSTED, // Ship ladders that lead nowhere
            1752 to BROKEN,
            4163 to UP_TWO_FLOORS, // Rellekka seer's house
            4164 to UP_TWO_FLOORS,
            4647 to NOTHING, // Burthorpe games room
            4648 to NOTHING,
            5812 to UP_TWO_FLOORS, // Seers' Village roof
            11041 to UP, // Underground, with floors above it
            12780 to UP_TWO_FLOORS, // Burgh de Rott store roof
            12781 to DOWN_TWO_FLOORS
        )

        /**
         * Returns the type of [ladder].
         *
         * The type follows from the menu actions: "Climb" goes up or down, "Climb-up" goes up (out of the dungeon from
         * an underground ground floor), and "Climb-down" goes down (into the dungeon from a surface ground floor).
         * [TYPES] covers the ladders where it doesn't.
         *
         * @param ladder The ladder.
         * @return The ladder type.
         */
        fun forObject(ladder: GameObject): LadderType {
            val type = TYPES[ladder.id]
            if (type != null) {
                return type
            }
            val actions = ladder.def().actions.filterNotNull().map { it.lowercase() }
            val groundFloor = ladder.position.z == 0
            val underground = ladder.position.y >= CELLAR_OFFSET
            return when {
                "climb" in actions || ("climb-up" in actions && "climb-down" in actions) -> UP_OR_DOWN
                "climb-up" in actions -> if (groundFloor && underground) OUT_OF_CELLAR else UP
                groundFloor && !underground -> INTO_CELLAR
                else -> DOWN
            }
        }
    }
}
