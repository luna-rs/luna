package api.bot.webwalk.plan

import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.model.EdgeAction
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.FairyRing
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportDefinition
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.model.WebWalkEdge
import api.bot.webwalk.model.WebWalkGraph
import api.bot.webwalk.model.WebWalkNode
import io.luna.game.model.Position
import java.util.PriorityQueue
import java.util.Random
import java.util.SplittableRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Plans trips between any two positions with the nodes and edges of a [WebWalkGraph].
 *
 * A trip is the cheapest way, for one bot at one time, to get from a start to a destination. The cost of a trip is its
 * time in ticks plus penalties, see [PlannerCosts]. What a bot can do is a [CapabilitySnapshot]: edges that it doesn't meet
 * the requirements of are not used, and neither are teleports that it can't use.
 *
 * - The start and the destination are linked to the nodes nearest to them, which are the ones that a [WalkEstimator] says
 *   they can be walked to. A trip that is short enough is walked directly.
 * - A trip can also have several places it may end at, and goes to the one that costs least. That is how the nearest bank is
 *   found, see [planToNearestBank].
 * - Edges can be taken from any node: walks, doors, ladders and the like, ships, and teleports. Teleports are not stored
 *   in the graph for each bot. Instead the ones that the snapshot allows are taken from wherever a bot is, as long as it
 *   meets their requirements there (which is how a bot deep in the wilderness has to walk out first). Fairy rings can be
 *   taken from any ring to any other, if the bot has the [CapabilitySnapshot.FAIRY_RINGS] unlock.
 * - The search is Dijkstra's algorithm. A* would need a heuristic that teleports can't break, and the graph is small enough
 *   not to need one.
 * - Bots can vary their route. The best trip is found first. A bot that is less than fully intelligent then tries other
 *   trips with the cost of every edge changed a little, and picks at random between the best trip and those of them that
 *   cost no more than a tolerance more, which gets smaller the more intelligent the bot is.
 *
 * The planner is made once and used from any thread, and it only reads what it is given.
 *
 * @param graph The graph.
 * @param estimator Links the start and the destination to the graph.
 * @param costs The weights of the costs.
 *
 * @author Hydrozoa
 */
class WebWalkPlanner(private val graph: WebWalkGraph,
                     private val estimator: WalkEstimator,
                     private val costs: PlannerCosts = PlannerCosts()) {

    companion object {

        /**
         * The width and length of the squares that nodes are sorted into, so that the ones nearby can be found quickly.
         */
        private const val CELL_SIZE = 32

        /**
         * The cost of what can't be reached.
         */
        private const val UNREACHED = Double.MAX_VALUE
    }

    /**
     * An edge, with the number of the node that it arrives at.
     */
    private class IndexedEdge(val edge: WebWalkEdge, val to: Int)

    /**
     * A node that the start or the destination is linked to, with the time that it takes to walk to or from it.
     */
    private class Link(val node: Int, val ticks: Int)

    /**
     * A way to arrive at one of the places that a trip may end at, with the time that it takes to walk the last stretch.
     */
    private class EndLink(val destination: Position, val ticks: Int)

    /**
     * A teleport, with the number of the node that it arrives at.
     */
    private class IndexedTeleport(val teleport: TeleportDefinition, val to: Int)

    /**
     * What the planner needs to know about one trip before it starts searching.
     */
    private class Request(val start: Position,
                          val snapshot: CapabilitySnapshot,
                          val startLinks: List<Link>,
                          val endLinks: Map<Int, List<EndLink>>,
                          val direct: List<EndLink>,
                          val teleports: List<IndexedTeleport>)

    /**
     * A trip that was found.
     */
    private class Found(val legs: List<PlanLeg>, val cost: Double) {

        /**
         * Tells trips apart, by where they go.
         */
        val key: String = legs.joinToString(">") { "${it.type}${it.to.x},${it.to.y},${it.to.z}" }
    }

    /**
     * The nodes of the graph, whose positions in this list are their numbers.
     */
    private val nodes: List<WebWalkNode> = graph.nodes.values.toList()

    /**
     * The number of each node, keyed by id.
     */
    private val indexById: Map<String, Int> = nodes.indices.associate { nodes[it].id to it }

    /**
     * The edges that leave each node.
     */
    private val outgoing: Array<List<IndexedEdge>> = Array(nodes.size) { index ->
        graph.edgesFrom(nodes[index].id).mapNotNull { edge -> indexById[edge.to]?.let { IndexedEdge(edge, it) } }
    }

    /**
     * The fairy rings, with the number of their nodes.
     */
    private val rings: List<Pair<Int, FairyRing>> = graph.fairyRings.mapNotNull { ring ->
        indexById[ring.nodeId]?.let { Pair(it, ring) }
    }

    /**
     * The fairy ring at each node that has one.
     */
    private val ringByNode: Map<Int, FairyRing> = rings.toMap()

    /**
     * The numbers of the nodes in each square of the map.
     */
    private val grid: Map<Triple<Int, Int, Int>, List<Int>> = nodes.indices.groupBy { cell(nodes[it].position) }

    /**
     * The tiles to stand on at the banks.
     */
    private val bankStands: Set<Position> = graph.banks.map { it.stand }.toSet()

    /**
     * The nodes that each bank is linked to, which are found when they are first needed. The banks and the web don't change,
     * so what can be walked to a bank is the same for every trip.
     */
    private val bankLinks = ConcurrentHashMap<Position, List<Link>>()

    /**
     * Plans a trip.
     *
     * @param start Where the trip starts.
     * @param destination Where the trip ends.
     * @param snapshot What the bot can do.
     * @param intelligence How intelligent the bot is, from `0.0` to `1.0`. A bot of `1.0` always takes the best trip.
     * @param random Where the variation of the trips of other bots comes from.
     * @return The trip, or `null` if the destination can't be reached. A trip from a position to itself has no legs.
     */
    fun plan(start: Position,
             destination: Position,
             snapshot: CapabilitySnapshot,
             intelligence: Double = 1.0,
             random: Random = Random()): WebWalkPlan? =
        planToNearest(start, listOf(destination), snapshot, intelligence, random)

    /**
     * Plans a trip to the nearest bank, which is the one that the cheapest trip ends at. The last leg of the trip ends at the
     * [BankDefinition.stand] of that bank.
     *
     * @param start Where the trip starts.
     * @param snapshot What the bot can do.
     * @param intelligence How intelligent the bot is, from `0.0` to `1.0`. A bot of `1.0` always takes the best trip, and
     * others may take a trip to another bank that is not much worse.
     * @param random Where the variation of the trips of other bots comes from.
     * @return The trip, or `null` if there are no banks or none can be reached. A trip from a bank to itself has no legs.
     */
    fun planToNearestBank(start: Position,
                          snapshot: CapabilitySnapshot,
                          intelligence: Double = 1.0,
                          random: Random = Random()): WebWalkPlan? =
        planToNearest(start, graph.banks.map { it.stand }, snapshot, intelligence, random)

    /**
     * Plans a trip to the nearest of some destinations, which is the one that the cheapest trip ends at.
     *
     * @param start Where the trip starts.
     * @param destinations The places the trip may end at.
     * @param snapshot What the bot can do.
     * @param intelligence How intelligent the bot is, from `0.0` to `1.0`. A bot of `1.0` always takes the best trip.
     * @param random Where the variation of the trips of other bots comes from.
     * @return The trip, or `null` if none of the destinations can be reached. A trip from a destination to itself has no legs.
     */
    fun planToNearest(start: Position,
                      destinations: Collection<Position>,
                      snapshot: CapabilitySnapshot,
                      intelligence: Double = 1.0,
                      random: Random = Random()): WebWalkPlan? {
        if (start in destinations) {
            return WebWalkPlan(emptyList(), 0.0)
        }
        val request = prepare(start, destinations.distinct(), snapshot)
        val best = search(request, null) ?: return null

        var chosen = best
        val unvaried = intelligence.coerceIn(0.0, 1.0)
        if (unvaried < 1.0 && costs.alternatives > 0) {
            val tolerance = maxOf(costs.toleranceMinimum, best.cost * costs.toleranceFraction) * (1.0 - unvaried)
            val options = LinkedHashMap<String, Found>()
            options[best.key] = best
            repeat(costs.alternatives) {
                val other = search(request, random.nextLong())
                if (other != null && other.cost <= best.cost + tolerance) {
                    options.putIfAbsent(other.key, other)
                }
            }
            chosen = options.values.toList()[random.nextInt(options.size)]
        }
        return WebWalkPlan(chosen.legs, chosen.cost)
    }

    /**
     * Links a trip to the graph, and finds the teleports that its bot can use.
     */
    private fun prepare(start: Position, destinations: List<Position>, snapshot: CapabilitySnapshot): Request {
        val startLinks = link(start) { estimator.ticks(start, it) }
        val endLinks = HashMap<Int, MutableList<EndLink>>()
        val direct = ArrayList<EndLink>()
        for (destination in destinations) {
            val links = if (destination in bankStands) bankLinks.computeIfAbsent(destination) { linkEnd(it) } else linkEnd(destination)
            for (link in links) {
                endLinks.getOrPut(link.node) { ArrayList() } += EndLink(destination, link.ticks)
            }
            if (start.z == destination.z && start.computeLongestDistance(destination) <= costs.linkRadius) {
                estimator.ticks(start, destination)?.let { direct += EndLink(destination, it) }
            }
        }
        val teleports = graph.teleports.filter { it.id in snapshot.usableTeleports }
            .mapNotNull { teleport -> indexById[teleport.nodeId]?.let { IndexedTeleport(teleport, it) } }
        return Request(start, snapshot, startLinks, endLinks, direct, teleports)
    }

    /**
     * Finds the nodes that a destination can be walked to from, nearest first.
     */
    private fun linkEnd(destination: Position): List<Link> = link(destination) { estimator.ticks(it, destination) }

    /**
     * Finds the nodes nearest to a position that it can be walked to or from, nearest first.
     *
     * @param position The position.
     * @param ticks Finds the time to walk between the position and the position of a node, or `null` if it can't be done.
     */
    private fun link(position: Position, ticks: (Position) -> Int?): List<Link> {
        val (cellX, cellY, cellZ) = cell(position)
        val reach = costs.linkRadius / CELL_SIZE + 1
        val candidates = ArrayList<Int>()
        for (x in cellX - reach..cellX + reach) {
            for (y in cellY - reach..cellY + reach) {
                for (index in grid[Triple(x, y, cellZ)] ?: continue) {
                    if (nodes[index].position.computeLongestDistance(position) <= costs.linkRadius) {
                        candidates += index
                    }
                }
            }
        }
        candidates.sortWith(compareBy({ nodes[it].position.computeLongestDistance(position) }, { it }))

        val links = ArrayList<Link>()
        for (index in candidates.take(costs.linkCandidates * 3)) {
            if (links.size >= costs.linkCandidates) {
                break
            }
            ticks(nodes[index].position)?.let { links += Link(index, it) }
        }
        return links
    }

    /**
     * Finds the cheapest trip of a request. With a seed, the cost of every edge is changed a little to find another trip,
     * but the cost of the trip that is found is still its real one.
     */
    private fun search(request: Request, seed: Long?): Found? {
        val start = nodes.size
        val end = nodes.size + 1
        val dist = DoubleArray(nodes.size + 2) { UNREACHED }
        val arrivals = arrayOfNulls<PlanLeg>(nodes.size + 2)
        val previous = IntArray(nodes.size + 2) { -1 }
        val queue = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })

        // The end is not a place, because a trip may end at one of several: the legs that arrive at it say which.
        fun position(index: Int) = if (index == start) request.start else nodes[index].position

        fun level(index: Int) = if (index < nodes.size) nodes[index].wildernessLevel else
            WebWalkNode.wildernessLevelOf(position(index))

        fun relax(from: Int, to: Int, leg: PlanLeg, key: Int) {
            val jitter = if (seed == null) 1.0 else
                1.0 + costs.jitter * SplittableRandom(seed + key * -0x61c8864680b583ebL).nextDouble()
            val total = dist[from] + leg.cost * jitter + costs.legPenalty
            if (total < dist[to]) {
                dist[to] = total
                previous[to] = from
                arrivals[to] = leg
                queue.add(Pair(total, to))
            }
        }

        // Takes a step of a number of ticks, with what that adds to the cost. A step to the end says where it ends.
        fun step(from: Int, to: Int, type: EdgeType, ticks: Double, extra: Double, action: EdgeAction? = null,
                 teleport: TeleportDefinition? = null, ring: FairyRing? = null, exposed: Boolean = true,
                 requirements: Requirements = Requirements.NONE, target: Position? = null) {
            val exposure = if (exposed) ticks else 0.0
            val arrival = target ?: position(to)
            val arrivalLevel = if (target != null) WebWalkNode.wildernessLevelOf(target) else level(to)
            val cost = ticks + extra + wildernessPenalty(level(from), arrivalLevel, exposure)
            val key = (from * 31 + to) * 31 + type.ordinal + (teleport?.id?.hashCode() ?: 0) + (target?.hashCode() ?: 0)
            relax(from, to, PlanLeg(type, position(from), arrival, cost, action, teleport, ring, requirements), key)
        }

        dist[start] = 0.0
        queue.add(Pair(0.0, start))
        while (queue.isNotEmpty()) {
            val (cost, node) = queue.poll()
            if (cost > dist[node]) {
                continue
            }
            if (node == end) {
                break
            }
            val here = level(node)

            if (node == start) {
                request.startLinks.forEach { step(start, it.node, EdgeType.WALK, it.ticks.toDouble(), 0.0) }
                for (link in request.direct) {
                    step(start, end, EdgeType.WALK, link.ticks.toDouble(), 0.0, target = link.destination)
                }
            } else {
                for (indexed in outgoing[node]) {
                    val edge = indexed.edge
                    if (!request.snapshot.meets(edge.requirements, here)) {
                        continue
                    }
                    val ticks = baseTicks(edge, node, indexed.to)
                    step(node, indexed.to, edge.type, ticks, edge.requirements.coins * costs.coinWeight, edge.action,
                         requirements = edge.requirements)
                }
                request.endLinks[node]?.forEach { step(node, end, EdgeType.WALK, it.ticks.toDouble(), 0.0, target = it.destination) }
                ringByNode[node]?.let { ring ->
                    if (CapabilitySnapshot.FAIRY_RINGS in request.snapshot.flags && request.snapshot.meets(ring.requirements, here)) {
                        for ((other, target) in rings) {
                            if (other != node && request.snapshot.meets(target.requirements, level(other))) {
                                step(node, other, EdgeType.FAIRY_RING, costs.fairyRingTicks, 0.0, ring = target)
                            }
                        }
                    }
                }
            }

            for (indexed in request.teleports) {
                val teleport = indexed.teleport
                if (indexed.to != node && request.snapshot.meets(teleport.requirements, here)) {
                    val penalty = when (teleport.kind) {
                        TeleportKind.SPELL -> costs.runePenalty
                        TeleportKind.JEWELLERY -> costs.chargePenalty
                        TeleportKind.HOME -> 0.0
                    }
                    step(node, indexed.to, EdgeType.TELEPORT, teleport.cost.toDouble(), penalty, teleport = teleport,
                         exposed = false)
                }
            }
        }

        if (dist[end] == UNREACHED) {
            return null
        }
        val legs = ArrayList<PlanLeg>()
        var node = end
        while (node != start) {
            legs += arrivals[node]!!
            node = previous[node]
        }
        legs.reverse()
        return Found(legs, legs.sumOf { it.cost })
    }

    /**
     * Finds the time that an edge takes, in ticks, which is what it says or else what its type usually takes.
     */
    private fun baseTicks(edge: WebWalkEdge, from: Int, to: Int): Double {
        edge.cost?.let { return it.toDouble() }
        return when (edge.type) {
            EdgeType.WALK -> ticksForTiles(nodes[from].position.computeLongestDistance(nodes[to].position)).toDouble()
            EdgeType.DOOR, EdgeType.GATE, EdgeType.CURTAIN -> costs.doorTicks
            EdgeType.LADDER, EdgeType.STAIR -> costs.ladderTicks
            EdgeType.TRAPDOOR -> costs.trapdoorTicks
            EdgeType.CROSSING -> costs.crossingTicks
            EdgeType.SHIP -> costs.shipTicks
            EdgeType.FAIRY_RING, EdgeType.TELEPORT -> costs.fairyRingTicks
        }
    }

    /**
     * Computes the penalty for being in the wilderness, which is large so that bots go around it, but not so large that
     * they never go through it when there is no other way.
     *
     * @param from The wilderness level where a step starts.
     * @param to The wilderness level where it ends.
     * @param exposedTicks The ticks of the step that are spent where a bot can be attacked.
     */
    private fun wildernessPenalty(from: Int, to: Int, exposedTicks: Double): Double {
        val level = maxOf(from, to)
        if (level == 0) {
            return 0.0
        }
        var penalty = exposedTicks * costs.wildernessWalkFactor * (1.0 + level * costs.wildernessLevelScale)
        if (from == 0) {
            penalty += costs.wildernessEntry
        }
        return penalty
    }

    /**
     * The square of the map that a position is in.
     */
    private fun cell(position: Position) = Triple(position.x / CELL_SIZE, position.y / CELL_SIZE, position.z)
}
