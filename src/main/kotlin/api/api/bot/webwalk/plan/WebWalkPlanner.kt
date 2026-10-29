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
import engine.controllers.WildernessLocatableController
import io.luna.game.model.Position
import io.luna.game.model.area.SimpleBoxArea
import java.util.PriorityQueue
import java.util.Random
import java.util.SplittableRandom

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
 *   found, see [planToNearestBank]. The nodes that a bank is walked to from are not searched for: the generator linked them,
 *   see [BankDefinition.links].
 * - Edges can be taken from any node: walks, doors, ladders and the like, ships, and teleports. Teleports are not stored
 *   in the graph for each bot. Instead the ones that the snapshot allows are taken from wherever a bot is, as long as it
 *   meets their requirements there (which is how a bot deep in the wilderness has to walk out first). The cost of a
 *   teleport doesn't depend on where it is cast from, except for one thing: one that lands in the wilderness costs
 *   [PlannerCosts.wildernessEntry] more when cast from outside it. So each teleport is only tried from the cheapest place
 *   outside the wilderness and the cheapest place inside it that it can be used from, see [search]. Fairy rings can be
 *   taken from any ring to any other, if the bot has the [CapabilitySnapshot.FAIRY_RINGS] unlock.
 * - The search is Dijkstra's algorithm. A* would need a heuristic that teleports can't break, and the graph is small enough
 *   not to need one.
 * - A trip spends what it pays with: the coins of fares and tolls, the runes and items of teleport spells, and the charges of
 *   teleport jewellery (see [CapabilitySnapshot.teleportSpends]). What is left is carried along the search, and a step that
 *   the bot can no longer pay for is not taken, so a bot with 40 coins is never sent on two fares of 30. Each node keeps
 *   the ways of arriving at it that are cheaper or leave more to spend than the others, up to [PlannerCosts.maxLabels].
 *   What a bot has plenty of isn't counted, see [PlannerCosts.abundantUses].
 * - Bots can vary their route. The best trip is found first. A bot that is less than fully intelligent then tries other
 *   trips with the cost of every edge changed a little, and picks at random between the best trip and those of them that
 *   cost no more than a tolerance more, which gets smaller the more intelligent the bot is.
 * - The trip that is chosen is then made to look less like a web. Walks in a row skip the nodes in between when the
 *   [WalkEstimator] can walk straight to a later one, within [PlannerCosts.shortcutRadius], for no more than the walks cost.
 *   Then every waypoint between two walks is moved to a random tile near it, within [PlannerCosts.scatterRadius], if both
 *   walks can still be walked, so that bots going the same way don't all step on the same tiles. Doors, ladders and the like,
 *   and the destination, are never moved.
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
                     val costs: PlannerCosts = PlannerCosts()) {

    companion object {

        /**
         * The width and length of the squares that nodes are sorted into, so that the ones nearby can be found quickly.
         */
        private const val CELL_SIZE = 32

        /**
         * Taverley Dungeon, whose tunnels wind so far that a node a few tiles away can be over a hundred steps off. Walks
         * there are never joined, and each one is given longer (see [api.bot.webwalk.walk.LegContext.walk]).
         */
        val TAVERLEY_DUNGEON = SimpleBoxArea.of(2816, 9664, 2943, 9870)
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
     * Something that a trip can spend and that runs out: an item, such as coins or a rune, or the charges of teleport
     * jewellery.
     */
    private sealed class Resource {

        /**
         * An item, by id.
         */
        data class Item(val id: Int) : Resource()

        /**
         * The charges of teleport jewellery, by the name of the jewellery.
         */
        data class Charge(val name: String) : Resource()
    }

    /**
     * What a trip can spend, as amounts of the [Resource]s that are counted, in the same order everywhere.
     *
     * @property initial What the bot has of each.
     * @property coinIndex Where coins are, or `-1` if they are not counted.
     * @property teleportSpends What each teleport spends of them, keyed by teleport id, for the ones that spend any.
     */
    private class Budget(val initial: IntArray, val coinIndex: Int, val teleportSpends: Map<String, IntArray>)

    /**
     * A way of arriving at a node of the search, with what is left to spend.
     *
     * @property node The number of the node.
     * @property cost The cost of the trip so far.
     * @property left What is left of each counted [Resource].
     * @property parent The label that this one was arrived at from, or `null` at the start.
     * @property leg The leg from the [parent], or `null` at the start.
     */
    private class Label(val node: Int, val cost: Double, val left: IntArray, val parent: Label?, val leg: PlanLeg?) {

        /**
         * If a better label replaced this one, so that it is skipped when it comes out of the queue.
         */
        var dead = false
    }

    /**
     * What the planner needs to know about one trip before it starts searching.
     */
    private class Request(val start: Position,
                          val snapshot: CapabilitySnapshot,
                          val startLinks: List<Link>,
                          val endLinks: Map<Int, List<EndLink>>,
                          val direct: List<EndLink>,
                          val teleports: List<IndexedTeleport>,
                          val budget: Budget)

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
     * The most coins that one edge of the graph costs, such as the fare of a ship or a toll, or `0` if none costs any.
     */
    private val largestFare: Int = graph.edges.maxOfOrNull { it.requirements.coins } ?: 0

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
     * The nodes that each bank is linked to, keyed by the tile to stand on at the bank, with the time that it takes to walk
     * from each. The generator found them, so what can be walked to a bank is the same for every trip and nothing has to be
     * searched for. Banks that share a stand share the links, and a node that two of them link keeps the quicker walk.
     */
    private val bankLinks: Map<Position, List<Link>> = graph.banks.groupBy { it.stand }.mapValues { (_, banks) ->
        banks.flatMap { bank -> bank.links.mapNotNull { link -> indexById[link.nodeId]?.let { Link(it, link.ticks) } } }
            .sortedBy { it.ticks }.distinctBy { it.node }
    }

    /**
     * Plans a trip.
     *
     * @param start Where the trip starts.
     * @param destination Where the trip ends.
     * @param snapshot What the bot can do.
     * @param intelligence How intelligent the bot is, from `0.0` to `1.0`. A bot of `1.0` always takes the best trip.
     * @param random Where the variation of the trips of other bots comes from.
     * @param refine Whether to shorten and scatter the trip (see [refine]). A trip that is only planned to be compared with
     * another, and may not be walked, is left as it is, and refined later if it is chosen.
     * @return The trip, or `null` if the destination can't be reached. A trip from a position to itself has no legs.
     */
    fun plan(start: Position,
             destination: Position,
             snapshot: CapabilitySnapshot,
             intelligence: Double = 1.0,
             random: Random = Random(),
             refine: Boolean = true): WebWalkPlan? =
        planToNearest(start, listOf(destination), snapshot, intelligence, random, refine)

    /**
     * Plans a trip to the nearest bank, which is the one that the cheapest trip ends at. The last leg of the trip ends at the
     * [BankDefinition.stand] of that bank.
     *
     * @param start Where the trip starts.
     * @param snapshot What the bot can do.
     * @param intelligence How intelligent the bot is, from `0.0` to `1.0`. A bot of `1.0` always takes the best trip, and
     * others may take a trip to another bank that is not much worse.
     * @param random Where the variation of the trips of other bots comes from.
     * @param refine Whether to shorten and scatter the trip, see [plan].
     * @return The trip, or `null` if there are no banks or none can be reached. A trip from a bank to itself has no legs.
     */
    fun planToNearestBank(start: Position,
                          snapshot: CapabilitySnapshot,
                          intelligence: Double = 1.0,
                          random: Random = Random(),
                          refine: Boolean = true): WebWalkPlan? =
        planToNearest(start, graph.banks.map { it.stand }, snapshot, intelligence, random, refine)

    /**
     * Plans a trip to the nearest of some destinations, which is the one that the cheapest trip ends at.
     *
     * @param start Where the trip starts.
     * @param destinations The places the trip may end at.
     * @param snapshot What the bot can do.
     * @param intelligence How intelligent the bot is, from `0.0` to `1.0`. A bot of `1.0` always takes the best trip.
     * @param random Where the variation of the trips of other bots comes from.
     * @param refine Whether to shorten and scatter the trip, see [plan].
     * @return The trip, or `null` if none of the destinations can be reached. A trip from a destination to itself has no legs.
     */
    fun planToNearest(start: Position,
                      destinations: Collection<Position>,
                      snapshot: CapabilitySnapshot,
                      intelligence: Double = 1.0,
                      random: Random = Random(),
                      refine: Boolean = true): WebWalkPlan? {
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
        val plan = WebWalkPlan(chosen.legs, chosen.legs.sumOf { it.cost })
        return if (refine) refine(plan, random) else plan
    }

    /**
     * Makes a trip look less like a web, before it is walked: walks in a row skip the nodes that they don't need to pass
     * through, and the waypoints between walks are moved a little (see the class documentation). Both ask the [WalkEstimator]
     * again and again, which can cost more than finding the trip, so a trip that is only compared with others is refined only
     * if it is the one that is walked.
     *
     * @param plan The trip, as the search found it.
     * @param random Where the variation of the waypoints comes from.
     * @return The refined trip, with the cost of its new legs.
     */
    fun refine(plan: WebWalkPlan, random: Random = Random()): WebWalkPlan {
        val legs = scatter(shorten(plan.legs), random)
        return WebWalkPlan(legs, legs.sumOf { it.cost })
    }

    /**
     * Determines if a leg is a walk that can be changed: one that has no requirements, which would be lost if it were.
     */
    private fun isPlainWalk(leg: PlanLeg) = leg.type == EdgeType.WALK && leg.requirements.isEmpty

    /**
     * Makes a walk between two positions on the same plane, with what the [estimator] says it takes.
     *
     * @return The walk, or `null` if it can't be walked.
     */
    private fun walk(from: Position, to: Position): PlanLeg? {
        if (from.z != to.z) {
            return null
        }
        val ticks = estimator.ticks(from, to)?.toDouble() ?: return null
        val penalty = wildernessPenalty(WildernessLocatableController.levelAt(from),
                                        WildernessLocatableController.levelAt(to), ticks)
        return PlanLeg(EdgeType.WALK, from, to, ticks + penalty)
    }

    /**
     * Skips nodes of a trip that don't need to be walked through. Each walk goes straight to the farthest node, among the walks
     * that follow it in a row, that is within [PlannerCosts.shortcutRadius] and can be walked to for no more than the walks
     * that it replaces. Walks in or into [TAVERLEY_DUNGEON] are kept as they are.
     *
     * @param legs The legs of the trip.
     * @return The legs, with the walks that were skipped joined.
     */
    private fun shorten(legs: List<PlanLeg>): List<PlanLeg> {
        val shortened = ArrayList<PlanLeg>(legs.size)
        var index = 0
        while (index < legs.size) {
            val leg = legs[index]
            var last = index
            if (isPlainWalk(leg)) {
                while (last + 1 < legs.size && isPlainWalk(legs[last + 1])) {
                    last++
                }
            }

            // The farthest node first, so that as many as possible are skipped.
            var joined = leg
            var end = last
            while (end > index) {
                val to = legs[end].to
                if (leg.from.z == to.z && leg.from.computeLongestDistance(to) <= costs.shortcutRadius &&
                    leg.from !in TAVERLEY_DUNGEON && to !in TAVERLEY_DUNGEON) {
                    val shortcut = walk(leg.from, to)
                    if (shortcut != null && shortcut.cost <= (index..end).sumOf { legs[it].cost } + 1e-9) {
                        joined = shortcut
                        break
                    }
                }
                end--
            }
            shortened += joined
            index = end + 1
        }
        return shortened
    }

    /**
     * Moves every waypoint between two walks to a random tile within [PlannerCosts.scatterRadius] of it, if both walks can
     * still be walked. A waypoint is left where it is if none of the tiles tried will do.
     *
     * @param legs The legs of the trip.
     * @param random Where the tiles come from.
     * @return The legs, with the waypoints moved.
     */
    private fun scatter(legs: List<PlanLeg>, random: Random): List<PlanLeg> {
        val radius = costs.scatterRadius
        if (radius <= 0) {
            return legs
        }
        val scattered = legs.toMutableList()
        for (index in 0 until scattered.size - 1) {
            val into = scattered[index]
            val onward = scattered[index + 1]
            if (!isPlainWalk(into) || !isPlainWalk(onward)) {
                continue
            }
            for (attempt in 0 until costs.scatterAttempts) {
                val dx = random.nextInt(radius * 2 + 1) - radius
                val dy = random.nextInt(radius * 2 + 1) - radius
                if (dx == 0 && dy == 0) {
                    continue
                }
                val tile = Position(into.to.x + dx, into.to.y + dy, into.to.z)
                val first = walk(into.from, tile) ?: continue
                val second = walk(tile, onward.to) ?: continue
                scattered[index] = first
                scattered[index + 1] = second
                break
            }
        }
        return scattered
    }

    /**
     * Links a trip to the graph, and finds the teleports that its bot can use.
     */
    private fun prepare(start: Position, destinations: List<Position>, snapshot: CapabilitySnapshot): Request {
        val startLinks = link(start) { estimator.ticks(start, it) }
        val endLinks = HashMap<Int, MutableList<EndLink>>()
        val direct = ArrayList<EndLink>()
        for (destination in destinations) {
            val links = bankLinks[destination] ?: linkEnd(destination)
            for (link in links) {
                endLinks.getOrPut(link.node) { ArrayList() } += EndLink(destination, link.ticks)
            }
            if (start.z == destination.z && start.computeLongestDistance(destination) <= costs.linkRadius) {
                estimator.ticks(start, destination)?.let { direct += EndLink(destination, it) }
            }
        }
        val teleports = graph.teleports.filter { it.id in snapshot.usableTeleports }
            .mapNotNull { teleport -> indexById[teleport.nodeId]?.let { IndexedTeleport(teleport, it) } }
        return Request(start, snapshot, startLinks, endLinks, direct, teleports, budget(snapshot, teleports))
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
     * Finds what a trip can spend: the coins, runes, items and jewellery charges that it has to count, because the bot has
     * too few of them to pay for everything the web might ask. The ones that it has plenty of (see
     * [PlannerCosts.abundantUses]) are left out, and with none to count the search is the same as one without spending.
     */
    private fun budget(snapshot: CapabilitySnapshot, teleports: List<IndexedTeleport>): Budget {
        val largest = HashMap<Resource, Int>()
        if (largestFare > 0) {
            largest[Resource.Item(CapabilitySnapshot.COINS)] = largestFare
        }
        for (indexed in teleports) {
            val spend = snapshot.teleportSpends[indexed.teleport.id] ?: continue
            spend.items.forEach { (id, amount) -> largest.merge(Resource.Item(id), amount, ::maxOf) }
            spend.charges.forEach { (name, amount) -> largest.merge(Resource.Charge(name), amount, ::maxOf) }
        }
        fun held(resource: Resource) = when (resource) {
            is Resource.Item -> snapshot.items[resource.id] ?: 0
            is Resource.Charge -> snapshot.charges[resource.name] ?: 0
        }
        val counted = largest.filter { (resource, most) -> held(resource).toLong() < costs.abundantUses.toLong() * most }
            .keys.toList()
        val index = counted.withIndex().associate { it.value to it.index }

        fun vector(spend: Spend): IntArray? {
            val amounts = IntArray(counted.size)
            spend.items.forEach { (id, amount) -> index[Resource.Item(id)]?.let { amounts[it] += amount } }
            spend.charges.forEach { (name, amount) -> index[Resource.Charge(name)]?.let { amounts[it] += amount } }
            return if (amounts.any { it > 0 }) amounts else null
        }
        val teleportSpends = HashMap<String, IntArray>()
        for (indexed in teleports) {
            snapshot.teleportSpends[indexed.teleport.id]?.let(::vector)?.let { teleportSpends[indexed.teleport.id] = it }
        }
        return Budget(IntArray(counted.size) { held(counted[it]) }, index[Resource.Item(CapabilitySnapshot.COINS)] ?: -1,
                      teleportSpends)
    }

    /**
     * Finds the cheapest trip of a request that the bot can pay for. With a seed, the cost of every edge is changed a
     * little to find another trip, but the cost of the trip that is found is still its real one.
     *
     * Each way of arriving at a node is a [Label], with what is left to spend. A label is only kept if no other at its node
     * is as cheap with as much left, so that a cheap way that spends everything doesn't hide a dearer one that can still
     * pay for what comes after. Without anything to count, every node keeps only its cheapest label.
     */
    private fun search(request: Request, seed: Long?): Found? {
        val start = nodes.size
        val end = nodes.size + 1
        val budget = request.budget
        val labels = Array(nodes.size + 2) { ArrayList<Label>(1) }
        val queue = PriorityQueue<Label>(compareBy { it.cost })

        // The end is not a place, because a trip may end at one of several: the legs that arrive at it say which.
        fun position(index: Int) = if (index == start) request.start else nodes[index].position

        fun level(index: Int) = if (index < nodes.size) nodes[index].wildernessLevel else
            WildernessLocatableController.levelAt(position(index))

        fun relax(from: Label, to: Int, leg: PlanLeg, key: Int, spend: IntArray?, coins: Int) {
            var left = from.left
            if (spend != null || (coins > 0 && budget.coinIndex >= 0)) {
                left = left.copyOf()
                spend?.forEachIndexed { index, amount -> left[index] -= amount }
                if (coins > 0 && budget.coinIndex >= 0) {
                    left[budget.coinIndex] -= coins
                }
                if (left.any { it < 0 }) {
                    return
                }
            }
            val jitter = if (seed == null) 1.0 else
                1.0 + costs.jitter * SplittableRandom(seed + key * -0x61c8864680b583ebL).nextDouble()
            val total = from.cost + leg.cost * jitter + costs.legPenalty
            val here = labels[to]
            if (here.any { it.cost <= total && covers(it.left, left) }) {
                return
            }
            here.removeAll { other -> (total <= other.cost && covers(left, other.left)).also { if (it) other.dead = true } }
            if (here.size >= costs.maxLabels) {
                val worst = here.maxBy { it.cost }
                if (worst.cost <= total) {
                    return
                }
                worst.dead = true
                here.remove(worst)
            }
            val label = Label(to, total, left, from, leg)
            here += label
            queue.add(label)
        }

        // Takes a step of a number of ticks, with what that adds to the cost and what it spends. A step to the end says
        // where it ends.
        fun step(from: Label, to: Int, type: EdgeType, ticks: Double, extra: Double, action: EdgeAction? = null,
                 teleport: TeleportDefinition? = null, ring: FairyRing? = null, exposed: Boolean = true,
                 requirements: Requirements = Requirements.NONE, target: Position? = null, spend: IntArray? = null) {
            val exposure = if (exposed) ticks else 0.0
            val arrival = target ?: position(to)
            val arrivalLevel = if (target != null) WildernessLocatableController.levelAt(target) else level(to)
            val cost = ticks + extra + wildernessPenalty(level(from.node), arrivalLevel, exposure)
            val key = (from.node * 31 + to) * 31 + type.ordinal + (teleport?.id?.hashCode() ?: 0) + (target?.hashCode() ?: 0)
            relax(from, to, PlanLeg(type, position(from.node), arrival, cost, action, teleport, ring, requirements), key,
                  spend, requirements.coins)
        }

        // A teleport doesn't cost the same from everywhere: one that lands in the wilderness costs the wilderness entry penalty
        // more when it is cast from outside it, and nothing more from inside it. Apart from that its cost doesn't depend on
        // where it is cast from. So the first label taken off the queue that can cast it from outside the wilderness, and the
        // first from inside it, are the cheapest places to cast it from, and it isn't tried from any other. That is
        // only so for a label that hasn't spent anything that is counted: one that has may not be able to pay for what
        // comes after, so the teleport is still tried from the labels after it.
        val triedOutside = BooleanArray(request.teleports.size)
        val triedInside = BooleanArray(request.teleports.size)
        var untried = request.teleports.size

        val first = Label(start, 0.0, budget.initial, null, null)
        labels[start] += first
        queue.add(first)
        var found: Label? = null
        while (queue.isNotEmpty()) {
            val label = queue.poll()
            if (label.dead) {
                continue
            }
            val node = label.node
            if (node == end) {
                found = label
                break
            }
            val here = level(node)

            if (node == start) {
                request.startLinks.forEach { step(label, it.node, EdgeType.WALK, it.ticks.toDouble(), 0.0) }
                for (link in request.direct) {
                    step(label, end, EdgeType.WALK, link.ticks.toDouble(), 0.0, target = link.destination)
                }
            } else {
                for (indexed in outgoing[node]) {
                    val edge = indexed.edge
                    if (!request.snapshot.meets(edge.requirements, here)) {
                        continue
                    }
                    val ticks = baseTicks(edge, node, indexed.to)
                    step(label, indexed.to, edge.type, ticks, edge.requirements.coins * costs.coinWeight, edge.action,
                         requirements = edge.requirements)
                }
                request.endLinks[node]?.forEach { step(label, end, EdgeType.WALK, it.ticks.toDouble(), 0.0, target = it.destination) }
                ringByNode[node]?.let { ring ->
                    if (CapabilitySnapshot.FAIRY_RINGS in request.snapshot.flags && request.snapshot.meets(ring.requirements, here)) {
                        for ((other, target) in rings) {
                            if (other != node && request.snapshot.meets(target.requirements, level(other))) {
                                step(label, other, EdgeType.FAIRY_RING, costs.fairyRingTicks, 0.0, ring = target)
                            }
                        }
                    }
                }
            }

            if (untried == 0) {
                continue
            }
            val wild = here > 0
            val unspent = label.left.contentEquals(budget.initial)
            for ((index, indexed) in request.teleports.withIndex()) {
                if (if (wild) triedInside[index] else triedOutside[index]) {
                    continue
                }
                val teleport = indexed.teleport
                if (indexed.to != node && request.snapshot.meets(teleport.requirements, here)) {
                    val penalty = when (teleport.kind) {
                        TeleportKind.SPELL -> costs.runePenalty
                        TeleportKind.JEWELLERY -> costs.chargePenalty
                        TeleportKind.HOME -> 0.0
                    }
                    step(label, indexed.to, EdgeType.TELEPORT, teleport.cost.toDouble(), penalty, teleport = teleport,
                         exposed = false, spend = budget.teleportSpends[teleport.id])
                    if (unspent) {
                        // Landing outside the wilderness costs the same from inside it as from outside it.
                        val anywhere = level(indexed.to) == 0
                        if (wild || anywhere) {
                            triedInside[index] = true
                        }
                        if (!wild || anywhere) {
                            triedOutside[index] = true
                        }
                        if (triedInside[index] && triedOutside[index]) {
                            untried--
                        }
                    }
                }
            }
        }

        val legs = ArrayList<PlanLeg>()
        var label = found ?: return null
        while (true) {
            legs += label.leg ?: break
            label = label.parent ?: break
        }
        legs.reverse()
        return Found(legs, legs.sumOf { it.cost })
    }

    /**
     * Determines if one amount left to spend is at least another, in everything that is counted.
     */
    private fun covers(left: IntArray, other: IntArray): Boolean {
        for (index in left.indices) {
            if (left[index] < other[index]) {
                return false
            }
        }
        return true
    }

    /**
     * Finds the time that an edge takes, in ticks, which is what it says or else what its type usually takes.
     */
    private fun baseTicks(edge: WebWalkEdge, from: Int, to: Int): Double {
        edge.cost?.let { return it.toDouble() }
        return when (edge.type) {
            EdgeType.WALK -> ticksForTiles(nodes[from].position.computeLongestDistance(nodes[to].position)).toDouble()
            EdgeType.DOOR, EdgeType.GATE, EdgeType.CURTAIN -> costs.doorTicks
            EdgeType.LADDER, EdgeType.STAIR, EdgeType.ENTRANCE -> costs.ladderTicks
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
