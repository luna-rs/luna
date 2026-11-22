package game.content.canoes

import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection

/**
 * What players are told at every arrival.
 */
private const val SINKS = "Your canoe sinks into the water after the hard journey."

/**
 * The places a canoe can paddle to, from south to north along the River Lum.
 *
 * @property tree The canoe tree at this station, or `null` where no canoe can be made.
 * @property landing Where players step ashore after paddling here.
 * @property sinkTile Where the canoe sinks after the journey.
 * @property sinkDirection Which way the sinking canoe faces.
 * @property mapLayer The destination map layer that offers this place.
 * @property mapButton The destination map button that picks this place.
 * @property arrival What players are told as they arrive, one chat line each.
 * @author TheLining
 */
enum class CanoeStation(val tree: CanoeTree?,
                        val landing: Position,
                        val sinkTile: Position,
                        val sinkDirection: ObjectDirection,
                        val mapLayer: Int,
                        val mapButton: Int,
                        val arrival: List<String>) {
    LUMBRIDGE(tree = CanoeTree(id = 12163,
                               stateVarbit = 1839,
                               chopTile = Position(3243, 3235),
                               chopFacing = Direction.WEST,
                               shapeTile = Position(3243, 3237),
                               hereLayer = 18271),
              landing = Position(3240, 3242),
              sinkTile = Position(3239, 3240),
              sinkDirection = ObjectDirection.NORTH,
              mapLayer = 18234,
              mapButton = 18255,
              arrival = listOf("You arrive at Lumbridge.", SINKS)),
    CHAMPIONS_GUILD(tree = CanoeTree(id = 12164,
                                     stateVarbit = 1840,
                                     chopTile = Position(3204, 3343),
                                     chopFacing = Direction.SOUTH,
                                     shapeTile = Position(3202, 3343),
                                     hereLayer = 18268),
                    landing = Position(3199, 3344),
                    sinkTile = Position(3197, 3342),
                    sinkDirection = ObjectDirection.NORTH,
                    mapLayer = 18236,
                    mapButton = 18256,
                    arrival = listOf("You arrive at the Champions' Guild.", SINKS)),
    BARBARIAN_VILLAGE(tree = CanoeTree(id = 12165,
                                       stateVarbit = 1841,
                                       chopTile = Position(3112, 3409),
                                       chopFacing = Direction.WEST,
                                       shapeTile = Position(3112, 3411),
                                       hereLayer = 18265),
                      landing = Position(3109, 3415),
                      sinkTile = Position(3109, 3411),
                      sinkDirection = ObjectDirection.NORTH,
                      mapLayer = 18238,
                      mapButton = 18257,
                      arrival = listOf("You arrive at the Barbarian Village.", SINKS)),
    EDGEVILLE(tree = CanoeTree(id = 12166,
                               stateVarbit = 1842,
                               chopTile = Position(3132, 3508),
                               chopFacing = Direction.WEST,
                               shapeTile = Position(3132, 3510),
                               hereLayer = 18259),
              landing = Position(3128, 3503),
              sinkTile = Position(3127, 3504),
              sinkDirection = ObjectDirection.NORTH,
              mapLayer = 18261,
              mapButton = 18262,
              arrival = listOf("You arrive at Edgeville.", SINKS)),

    /**
     * The pond in level 35 Wilderness. Only a waka gets here, and there's no canoe tree to paddle back from.
     */
    WILDERNESS_POND(tree = null,
                    landing = Position(3141, 3796),
                    sinkTile = Position(3142, 3795),
                    sinkDirection = ObjectDirection.EAST,
                    mapLayer = 18254,
                    mapButton = 18258,
                    arrival = listOf("You arrive in the Wilderness. There are no trees suitable to make a canoe.",
                                     SINKS,
                                     "Looks like you're walking back."));

    /**
     * The places a [type] canoe launched here can paddle to.
     */
    fun destinations(type: CanoeType): List<CanoeStation> = values().filter {
        when {
            it == this -> false
            it.tree == null -> type == CanoeType.WAKA
            else -> kotlin.math.abs(it.ordinal - ordinal) <= type.range
        }
    }

    companion object {

        /**
         * The station with the canoe tree [id], or `null` if there's none.
         */
        fun forTree(id: Int): CanoeStation? = values().firstOrNull { it.tree?.id == id }
    }
}

/**
 * A station's canoe tree: an object that shows each player their own tree, fallen tree or canoe.
 *
 * @property id The object.
 * @property stateVarbit The varbit that picks what the object shows.
 * @property chopTile Where players stand to chop the tree down.
 * @property chopFacing Which way they face while chopping.
 * @property shapeTile Where players stand to push the canoe into the water, and around which they shape it.
 * @property hereLayer The destination map layer that marks this station as where the player is.
 */
class CanoeTree(val id: Int,
                val stateVarbit: Int,
                val chopTile: Position,
                val chopFacing: Direction,
                val shapeTile: Position,
                val hereLayer: Int)
