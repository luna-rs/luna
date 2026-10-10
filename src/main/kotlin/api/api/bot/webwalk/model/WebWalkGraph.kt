package api.bot.webwalk.model

import io.luna.game.model.Position

/**
 * The kind of place a [WebWalkNode] stands for.
 *
 * @author Hydrozoa
 */
enum class NodeKind {

    /**
     * A point the planner may route through, such as a town centre.
     */
    WAYPOINT,

    /**
     * A tile on either side of a door, gate, curtain, ladder, stair or trapdoor.
     */
    OBSTACLE,

    /**
     * Where a teleport leaves the bot.
     */
    TELEPORT_DEST,

    /**
     * A place a ship sails from or to.
     */
    SHIP_PORT,

    /**
     * A fairy ring.
     */
    FAIRY_RING
}

/**
 * The way an edge of the graph is travelled.
 *
 * @property bidirectionalByDefault Whether data for this type is travelled both ways unless it says otherwise.
 *
 * @author Hydrozoa
 */
enum class EdgeType(val bidirectionalByDefault: Boolean) {

    /**
     * Walking between two nodes, which the pathfinder routes when the edge is executed.
     */
    WALK(true),

    /**
     * A door, opened if it is closed.
     */
    DOOR(true),

    /**
     * A gate, opened if it is closed.
     */
    GATE(true),

    /**
     * A curtain, opened if it is closed.
     */
    CURTAIN(true),

    /**
     * A ladder, climbed in the direction of the edge.
     */
    LADDER(false),

    /**
     * A staircase, climbed in the direction of the edge.
     */
    STAIR(false),

    /**
     * A trapdoor, climbed in the direction of the edge.
     */
    TRAPDOOR(false),

    /**
     * A crossing between two nearby tiles that needs more than a click on an object, such as a gate that charges a toll. The
     * [EdgeAction.handler] of the edge says how it is crossed.
     */
    CROSSING(true),

    /**
     * A ship between two ports.
     */
    SHIP(true),

    /**
     * A teleport. Never loaded from data: the planner makes these from the bot's [TeleportDefinition]s.
     */
    TELEPORT(false),

    /**
     * A fairy ring trip. Never loaded from data: the planner makes these from the [FairyRing]s.
     */
    FAIRY_RING(false);

    /**
     * @return `true` if this is a door, gate, curtain or crossing, which are steps between two nearby tiles on one plane.
     */
    val isDoorLike: Boolean
        get() = this == DOOR || this == GATE || this == CURTAIN || this == CROSSING

    /**
     * @return `true` if this type is made from an entry of `obstacles.jsonc`.
     */
    val isObstacle: Boolean
        get() = isDoorLike || this == LADDER || this == STAIR || this == TRAPDOOR
}

/**
 * A place in the web-walker graph.
 *
 * @property id The unique id of the node.
 * @property position The position of the node.
 * @property kind What the node stands for.
 * @property tags Free-form labels, such as `town`.
 * @property wildernessLevel The wilderness level at [position], or `0` outside the wilderness.
 *
 * @author Hydrozoa
 */
data class WebWalkNode(val id: String,
                       val position: Position,
                       val kind: NodeKind,
                       val tags: Set<String> = emptySet(),
                       val wildernessLevel: Int = wildernessLevelOf(position)) {

    companion object {

        /**
         * The wilderness level at a position, which is `0` outside the wilderness. This mirrors the area and the formula
         * of `WildernessController`.
         */
        fun wildernessLevelOf(position: Position): Int {
            val inWilderness = position.x in 2944..3392 && position.y in 3519..3966
            return if (inWilderness) ((position.y - 3520) / 8) + 1 else 0
        }
    }
}

/**
 * What the executor does to cross an edge, beyond walking to its start.
 *
 * @property objectId The id of the object to use, for doors, ladders and the like.
 * @property objectPosition The position of that object.
 * @property option The name of the menu option to use on the object, or `null` to choose by the object's type.
 * @property handler The name of the crossing handler that crosses the edge, for [EdgeType.CROSSING].
 *
 * @author Hydrozoa
 */
data class EdgeAction(val objectId: Int? = null,
                      val objectPosition: Position? = null,
                      val option: String? = null,
                      val handler: String? = null)

/**
 * A directed connection between two nodes. Data that is travelled both ways is loaded as two edges.
 *
 * @property from The id of the node the edge leaves.
 * @property to The id of the node the edge arrives at.
 * @property type How the edge is travelled.
 * @property cost The cost in game ticks, or `null` to have it estimated from the distance.
 * @property requirements What the bot needs to use the edge.
 * @property action What the executor does to cross the edge, if anything.
 *
 * @author Hydrozoa
 */
data class WebWalkEdge(val from: String,
                       val to: String,
                       val type: EdgeType,
                       val cost: Int? = null,
                       val requirements: Requirements = Requirements.NONE,
                       val action: EdgeAction? = null) {

    /**
     * @return The same edge travelled in the opposite direction.
     */
    fun reversed(): WebWalkEdge = copy(from = to, to = from)
}

/**
 * The ways a teleport can be cast or used.
 *
 * @author Hydrozoa
 */
enum class TeleportKind {

    /**
     * A teleport spell.
     */
    SPELL,

    /**
     * A menu option of teleport jewellery.
     */
    JEWELLERY,

    /**
     * The home teleport.
     */
    HOME
}

/**
 * A teleport that a bot may use from anywhere. Whether a bot can use it right now depends on the bot, so teleports are
 * not edges of the graph. The planner makes edges from the ones that a bot's snapshot allows.
 *
 * @property id The unique id of the teleport.
 * @property kind How the teleport is used.
 * @property key The name of the spell or jewellery (`null` for the home teleport).
 * @property option The menu option of the jewellery (only for [TeleportKind.JEWELLERY]).
 * @property destination Where the teleport arrives.
 * @property nodeId The id of the [NodeKind.TELEPORT_DEST] node at [destination].
 * @property cost The cost in game ticks.
 * @property requirements What the bot needs to use the teleport on top of what the spell or jewellery needs.
 *
 * @author Hydrozoa
 */
data class TeleportDefinition(val id: String,
                              val kind: TeleportKind,
                              val key: String?,
                              val option: Int?,
                              val destination: Position,
                              val nodeId: String,
                              val cost: Int,
                              val requirements: Requirements = Requirements.NONE)

/**
 * A fairy ring. Trips between rings are made by the planner, not stored.
 *
 * @property id The unique id of the ring.
 * @property code The three letters that are dialled to travel to the ring.
 * @property position Where the ring is.
 * @property nodeId The id of the [NodeKind.FAIRY_RING] node of the ring.
 * @property requirements What the bot needs to use the ring.
 *
 * @author Hydrozoa
 */
data class FairyRing(val id: String,
                     val code: String,
                     val position: Position,
                     val nodeId: String,
                     val requirements: Requirements = Requirements.NONE)

/**
 * A bank booth, with the tile in front of it to stand on. Banks are not nodes of the graph: they are only places for a trip to
 * end, so that the planner can find the nearest one. A bot at [stand] is the right side of the counter to use the booth.
 *
 * @property id The unique id of the bank.
 * @property booth Where the booth is.
 * @property stand The tile in front of the booth, that a bot walks to.
 *
 * @author Hydrozoa
 */
data class BankDefinition(val id: String, val booth: Position, val stand: Position)

/**
 * The immutable web-walker graph, which is safe to share between threads.
 *
 * Use [api.bot.webwalk.data.WebWalkLoader] to create one.
 *
 * @property teleports The teleports a bot may use.
 * @property fairyRings The fairy rings.
 * @property banks The banks that a trip can end at.
 *
 * @author Hydrozoa
 */
class WebWalkGraph(nodes: Collection<WebWalkNode>,
                   edges: Collection<WebWalkEdge>,
                   val teleports: List<TeleportDefinition>,
                   val fairyRings: List<FairyRing>,
                   val banks: List<BankDefinition> = emptyList()) {

    companion object {

        /**
         * A graph with nothing in it.
         */
        val EMPTY = WebWalkGraph(emptyList(), emptyList(), emptyList(), emptyList())
    }

    /**
     * The nodes, keyed by id.
     */
    val nodes: Map<String, WebWalkNode> = nodes.associateBy { it.id }

    /**
     * Every directed edge.
     */
    val edges: List<WebWalkEdge> = edges.toList()

    /**
     * The edges that leave each node, keyed by node id.
     */
    private val outgoing: Map<String, List<WebWalkEdge>> = this.edges.groupBy { it.from }

    /**
     * @return The node with [id], or `null` if there is none.
     */
    fun node(id: String): WebWalkNode? = nodes[id]

    /**
     * @return The edges that leave the node with [id].
     */
    fun edgesFrom(id: String): List<WebWalkEdge> = outgoing[id] ?: emptyList()
}
