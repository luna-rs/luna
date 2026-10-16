package api.bot.webwalk.generate

import api.bot.webwalk.model.WebWalkNode
import api.bot.webwalk.plan.DoorCrossings
import io.luna.game.model.Position

/**
 * Finds the walk edges that connect the nodes of the web-walker graph.
 *
 * Every node is linked to the nodes nearest to it by the walk, the ones that take the fewest game ticks to walk to. A node
 * that is already linked to another one, from either side, counts towards the nodes that it is linked to. It is not linked
 * to a node if that only makes a second link of a walk
 * that is already linked. That is when a node beside one of the two (a short walk away, and not through a door, as the
 * pathfinder finds it) is linked to a node beside the other. This leaves one link between the two leaves of a double door or a gate and whatever
 * they are linked to, whatever kind of node that is, rather than one for each leaf. The nodes that are left in groups that
 * are not linked to each other are then joined, nearest first, over longer distances. Once it is done, any two nodes that
 * can be walked between are connected, however far apart they are.
 *
 * The linker only asks how far it is to walk between two positions, so it does not depend on any pathfinder.
 *
 * @param pathCost Gives the cost, in game ticks, of walking between two positions on the same plane, or `null` if it can't
 * be walked. This may be called from any thread, but the linker never calls it from more than one at once.
 * @param neighbours How many nodes each node is linked to at first, which includes the ones that are linked to it. A node
 * that other nodes pick can end up with more links than this.
 * @param nearRadius How far, in tiles, the nodes that are linked at first may be.
 * @param farRadius How far, in tiles, nodes in other groups are looked for when joining the groups.
 * @param joinAttempts How many times a pair of groups is tried before giving up on joining them.
 * @param doors The doors of the graph. The tiles on either side of one are already connected by the door, so they are not
 * linked by a walk, which would only be a way around the door. They count as linked for the nodes that they are.
 * @param besideTicks The longest walk, in game ticks, between two nodes for one to be beside the other. A ladder or a
 * trapdoor in the way makes the walk a few ticks, but a wall makes it many more.
 * @param besideRadius How far, in tiles, a node that is beside another one may be. The walk between them is what decides.
 *
 * @author Hydrozoa
 */
class WalkLinker(private val pathCost: (Position, Position) -> Int?,
                 private val neighbours: Int = 5,
                 private val nearRadius: Int = 40,
                 private val farRadius: Int = 400,
                 private val joinAttempts: Int = 5,
                 private val doors: DoorCrossings? = null,
                 private val besideTicks: Int = 4,
                 private val besideRadius: Int = 3) {

    companion object {

        /**
         * The width of the squares that the nodes are sorted into, so that the ones nearby can be found quickly.
         */
        private const val CELL_SIZE = 32
    }

    /**
     * Finds the groups of nodes that are linked.
     */
    private class Groups(size: Int) {

        /**
         * The parent of each node, which is itself for the root of a group.
         */
        private val parent = IntArray(size) { it }

        /**
         * @return The root of the group that [node] is in.
         */
        fun find(node: Int): Int {
            var root = node
            while (parent[root] != root) {
                root = parent[root]
            }
            var current = node
            while (parent[current] != root) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }

        /**
         * Puts the groups of two nodes together.
         */
        fun union(a: Int, b: Int) {
            parent[find(a)] = find(b)
        }
    }

    /**
     * Makes the links.
     *
     * @param nodes The nodes to link. Nodes on different planes are never linked.
     * @param componentOf Gives the component of a node, as found by [WalkableComponents]. Nodes in different components
     * can not be walked between, so they are never tried, which saves the searches that would fail. By default every node
     * is in the same component.
     * @return The links, each of which is travelled both ways. They are always the same for the same nodes.
     */
    fun link(nodes: Collection<WebWalkNode>, componentOf: (WebWalkNode) -> Int = { 0 }): List<GeneratedLink> {
        val sorted = nodes.sortedBy { it.id }
        val components = IntArray(sorted.size) { componentOf(sorted[it]) }
        val grid = HashMap<Triple<Int, Int, Int>, MutableList<Int>>()
        for ((index, node) in sorted.withIndex()) {
            grid.getOrPut(cell(node.position)) { ArrayList() } += index
        }

        val links = ArrayList<GeneratedLink>()
        val groups = Groups(sorted.size)
        val attempted = HashSet<Pair<Int, Int>>()
        val linked = HashSet<Pair<Int, Int>>()
        val connected = HashSet<Pair<Int, Int>>()
        val costs = HashMap<Pair<Int, Int>, Int?>()

        fun keyOf(a: Int, b: Int) = if (a < b) Pair(a, b) else Pair(b, a)

        // The cost of walking between two nodes, which is only asked of the pathfinder once.
        fun cost(a: Int, b: Int): Int? = costs.getOrPut(keyOf(a, b)) { pathCost(sorted[a].position, sorted[b].position) }

        // Tries to link two nodes, once. Returns true if they are now linked.
        fun tryLink(a: Int, b: Int): Boolean {
            val key = keyOf(a, b)
            if (!attempted.add(key)) {
                return false
            }
            if (doors != null && doors.isCrossing(sorted[a].position, sorted[b].position)) {
                connected += key
                groups.union(a, b)
                return true
            }
            val cost = cost(a, b) ?: return false
            links += GeneratedLink(sorted[a].id, sorted[b].id, cost)
            linked += key
            connected += key
            groups.union(a, b)
            return true
        }

        // The nodes that could be beside a node, which are the ones within [besideRadius] tiles. Whether one is, is up to the walk.
        val nextToCache = HashMap<Int, List<Int>>()
        fun nextTo(index: Int): List<Int> = nextToCache.getOrPut(index) {
            val position = sorted[index].position
            val (cellX, cellY, cellZ) = cell(position)
            val reach = besideRadius / CELL_SIZE + 1
            val found = ArrayList<Int>()
            for (x in cellX - reach..cellX + reach) {
                for (y in cellY - reach..cellY + reach) {
                    for (other in grid[Triple(x, y, cellZ)] ?: continue) {
                        if (other != index && sorted[other].position.computeLongestDistance(position) <= besideRadius) {
                            found += other
                        }
                    }
                }
            }
            found
        }

        // Determines if a node is beside another one, which is when the walk between them is short, and not through a door. A
        // ladder or a trapdoor in the way makes the walk a few ticks, and a wall makes it a lot.
        fun beside(a: Int, b: Int): Boolean {
            if (a == b) {
                return true
            }
            if (components[a] != components[b] || doors != null && doors.isCrossing(sorted[a].position, sorted[b].position)) {
                return false
            }
            return cost(a, b)?.let { it <= besideTicks } ?: false
        }

        // Determines if two nodes would be a second link of the same walk. This is when a node beside one of them is already
        // linked to a node beside the other, like the two leaves of a double door or a gate are, to the leaves of another one.
        // A link of one of the two to a node beside itself doesn't get from the one to the other, so it doesn't count.
        fun repeatsLink(a: Int, b: Int): Boolean {
            val besideB = listOf(b) + nextTo(b)
            for (first in listOf(a) + nextTo(a)) {
                for (second in besideB) {
                    if (first != second && first != b && second != a && keyOf(first, second) in linked &&
                        beside(first, a) && beside(second, b)) {
                        return true
                    }
                }
            }
            return false
        }

        // Puts the nodes near a node in the order of the walk to them, the shortest first, which is not the order of the
        // distance. The ones that can't be walked to are left out. A door is crossed in the step between the two tiles beside it.
        fun byWalk(index: Int, candidates: List<Int>): List<Int> {
            val position = sorted[index].position
            return candidates.mapNotNull { other ->
                val distance = sorted[other].position.computeLongestDistance(position)
                val ticks = if (doors != null && doors.isCrossing(position, sorted[other].position)) distance else cost(index, other)
                ticks?.let { Triple(other, it, distance) }
            }.sortedWith(compareBy({ it.second }, { it.third }, { it.first })).map { it.first }
        }

        // Near links: each node to its closest nodes by the walk. The ones that are linked already, from either side, and the
        // ones that would only repeat a link count as linked, since the node gets there already.
        for (index in sorted.indices) {
            var count = 0
            for (other in byWalk(index, nearest(sorted, components, grid, index, nearRadius))) {
                if (count >= neighbours) {
                    break
                }
                if (keyOf(index, other) in connected) {
                    count++
                } else if (keyOf(index, other) !in attempted && repeatsLink(index, other)) {
                    count++
                } else if (tryLink(index, other)) {
                    count++
                }
            }
        }

        // Far links: join the groups that are left, which can only be done once for each pair of groups.
        // A search that fails can cover everything that can be reached, so each pair of groups is only tried a few times.
        val failures = HashMap<Pair<Int, Int>, Int>()
        for (index in sorted.indices) {
            for (other in byWalk(index, nearest(sorted, components, grid, index, farRadius))) {
                val a = groups.find(index)
                val b = groups.find(other)
                if (a == b) {
                    continue
                }
                val pair = if (a < b) Pair(a, b) else Pair(b, a)
                if ((failures[pair] ?: 0) >= joinAttempts) {
                    continue
                }
                if (tryLink(index, other)) {
                    break
                }
                failures.merge(pair, 1, Int::plus)
            }
        }
        return links
    }

    /**
     * Finds the nodes on the same plane within [radius] tiles of a node, which is the closest first.
     */
    private fun nearest(nodes: List<WebWalkNode>,
                        components: IntArray,
                        grid: Map<Triple<Int, Int, Int>, List<Int>>,
                        index: Int,
                        radius: Int): List<Int> {
        val position = nodes[index].position
        val (cellX, cellY, cellZ) = cell(position)
        val reach = radius / CELL_SIZE + 1
        val found = ArrayList<Int>()
        for (x in cellX - reach..cellX + reach) {
            for (y in cellY - reach..cellY + reach) {
                for (other in grid[Triple(x, y, cellZ)] ?: continue) {
                    if (other != index && components[other] == components[index] && nodes[other].position.computeLongestDistance(position) <= radius) {
                        found += other
                    }
                }
            }
        }
        return found.sortedWith(compareBy({ nodes[it].position.computeLongestDistance(position) }, { it }))
    }

    /**
     * The square of the grid that a position is in.
     */
    private fun cell(position: Position) = Triple(position.x / CELL_SIZE, position.y / CELL_SIZE, position.z)
}
