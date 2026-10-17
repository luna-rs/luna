package api.bot.webwalk.plan

import api.bot.webwalk.model.WebWalkGraph
import io.luna.game.model.Position
import kotlin.math.sign

/**
 * The steps between tiles that a door, gate or curtain of the web stands in.
 *
 * A walk that goes through one of these is only as good as the door is open, so the web crosses them with the edge of the
 * door, which opens it if it was closed. This is what a [WalkEstimator] uses to tell that a route is not a walk.
 *
 * @param steps The two tiles on either side of each door, which are next to each other.
 *
 * @author Hydrozoa
 */
class DoorCrossings(steps: Iterable<Pair<Position, Position>>) {

    companion object {

        /**
         * Finds the crossings of the doors, gates and curtains of a graph.
         */
        fun of(graph: WebWalkGraph): DoorCrossings = DoorCrossings(graph.edges.filter { it.type.isDoorLike }.mapNotNull { edge ->
            val from = graph.node(edge.from)?.position
            val to = graph.node(edge.to)?.position
            if (from == null || to == null) null else Pair(from, to)
        })
    }

    /**
     * Every crossing, in both directions.
     */
    private val crossings: Set<Pair<Position, Position>> = steps.flatMap { listOf(it, Pair(it.second, it.first)) }.toHashSet()

    /**
     * Determines if two positions are the tiles on either side of a door, in either order.
     */
    fun isCrossing(a: Position, b: Position): Boolean = Pair(a, b) in crossings

    /**
     * Determines if a route goes through a door.
     *
     * @param start Where the route starts.
     * @param waypoints The waypoints of the route, where it changes direction, with the end of it last.
     * @return `true` if one of its steps goes from one side of a door to the other. A diagonal step is taken to if either
     * of the two straight steps that it could be made of does.
     */
    fun crosses(start: Position, waypoints: Iterable<Position>): Boolean {
        if (crossings.isEmpty()) {
            return false
        }
        var current = start
        for (waypoint in waypoints) {
            while (current != waypoint) {
                val dx = sign((waypoint.x - current.x).toDouble()).toInt()
                val dy = sign((waypoint.y - current.y).toDouble()).toInt()
                if (crosses(current, dx, dy)) {
                    return true
                }
                current = Position(current.x + dx, current.y + dy, current.z)
            }
        }
        return false
    }

    /**
     * Determines if a single step goes through a door.
     */
    private fun crosses(from: Position, dx: Int, dy: Int): Boolean {
        val to = Position(from.x + dx, from.y + dy, from.z)
        if (dx == 0 || dy == 0) {
            return Pair(from, to) in crossings
        }
        val sideways = Position(from.x + dx, from.y, from.z)
        val ahead = Position(from.x, from.y + dy, from.z)
        return Pair(from, sideways) in crossings || Pair(sideways, to) in crossings ||
                Pair(from, ahead) in crossings || Pair(ahead, to) in crossings
    }
}
