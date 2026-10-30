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
 * @param doorTiles The tiles that diagonal doors stand on. A diagonal door fills its tile, so it is crossed through that tile
 * once it is open, between two tiles that are diagonally next to each other.
 *
 * @author Hydrozoa
 */
class DoorCrossings(steps: Iterable<Pair<Position, Position>>, doorTiles: Iterable<Position> = emptyList()) {

    companion object {

        /**
         * Finds the crossings of the doors, gates and curtains of a graph, and of the crossings between tiles next to each
         * other. A crossing that leads farther, such as down a trapdoor, is no step of a walk, so it is left out.
         */
        fun of(graph: WebWalkGraph): DoorCrossings {
            val steps = ArrayList<Pair<Position, Position>>()
            val doorTiles = ArrayList<Position>()
            for (edge in graph.edges) {
                if (!edge.type.isDoorLike) {
                    continue
                }
                val from = graph.node(edge.from)?.position ?: continue
                val to = graph.node(edge.to)?.position ?: continue
                if (from.z != to.z || from.computeLongestDistance(to) != 1) {
                    continue
                }
                steps += Pair(from, to)
                if (from.x != to.x && from.y != to.y) {
                    // The door is on the plane that it is crossed on, even where the map data has it a level up on a bridge.
                    val door = edge.action?.objectPosition?.let { Position(it.x, it.y, from.z) } ?: continue
                    if (door.isNextTo(from) && door.isNextTo(to)) {
                        doorTiles += door
                    }
                }
            }
            return DoorCrossings(steps, doorTiles)
        }

        /**
         * Determines if two tiles on the same plane are next to each other, and not diagonally.
         */
        private fun Position.isNextTo(other: Position): Boolean =
            z == other.z && Math.abs(x - other.x) + Math.abs(y - other.y) == 1
    }

    /**
     * Every crossing, in both directions.
     */
    private val crossings: Set<Pair<Position, Position>> = steps.flatMap { listOf(it, Pair(it.second, it.first)) }.toHashSet()

    /**
     * The tiles of the diagonal doors.
     */
    private val doorTiles: Set<Position> = doorTiles.toHashSet()

    /**
     * Determines if two positions are the tiles on either side of a door, in either order.
     */
    fun isCrossing(a: Position, b: Position): Boolean = Pair(a, b) in crossings

    /**
     * Determines if a route goes through a door.
     *
     * @param start Where the route starts.
     * @param waypoints The waypoints of the route, where it changes direction, with the end of it last.
     * @return `true` if one of its steps goes from one side of a door to the other, or onto the tile of a diagonal door. A
     * diagonal step is taken to if either of the two straight steps that it could be made of does.
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
        if (Pair(from, to) in crossings || to in doorTiles) {
            return true
        }
        if (dx == 0 || dy == 0) {
            return false
        }
        val sideways = Position(from.x + dx, from.y, from.z)
        val ahead = Position(from.x, from.y + dy, from.z)
        return Pair(from, sideways) in crossings || Pair(sideways, to) in crossings ||
                Pair(from, ahead) in crossings || Pair(ahead, to) in crossings ||
                sideways in doorTiles || ahead in doorTiles
    }
}
