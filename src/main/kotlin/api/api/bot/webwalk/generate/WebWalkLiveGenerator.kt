package api.bot.webwalk.generate

import api.bot.webwalk.data.WebWalkDataException
import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.plan.BotConditions
import api.bot.webwalk.plan.DoorCrossings
import api.bot.webwalk.plan.PathfinderWalkEstimator
import api.bot.webwalk.walk.ship.ShipBoarding
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.predef.*
import game.content.sailing.ShipGangplanks
import game.content.sailing.ShipRoute
import game.item.degradable.jewellery.TeleportJewellery
import game.obj.entrances.Entrance
import game.skill.magic.teleportSpells.TeleportAction
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.Luna
import engine.bank.Banking
import engine.obj.LadderDestination
import engine.obj.LadderType
import engine.obj.StairDestination
import engine.obj.Trapdoor
import engine.obj.TrapdoorLanding
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.path.route.RouteStrategy
import io.luna.game.model.path.route.StepValidator
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.Executor

/**
 * Makes the generated files of the web-walker: `obstacles.jsonc`, `climbs.jsonc`, `crossings.jsonc`, `teleports.jsonc`,
 * `banks.jsonc`, `walk_graph.jsonc` and `ships.jsonc`.
 * Everything is made in a single run of the server, because most of it needs a running world. The `generateWebWalk` Gradle task
 * makes it on demand, and the server makes it itself when it starts without the files, see [WebWalkGenerationRunner]. The files start with a comment that says they are generated and when, see [WebWalkWriter.header].
 *
 * - The closed doors, gates and curtains of `obstacles.jsonc` are made by [WebWalkDoorGenerator] from the map data of the
 *   cache.
 * - The crossings of `crossings.jsonc` are the objects of the [SpecialCrossings] that the cache's map data places, such as the
 *   leaves of the Al Kharid toll gate, or the fixed tiles of their placements, such as the H.A.M. Hideout trapdoor, made by
 *   [WebWalkCrossingGenerator].
 * - The ladders, stairs, trapdoors and entrances of `climbs.jsonc` land where [LadderDestination], [StairDestination],
 *   [Trapdoor] and [Entrance] say that they do, which is what players get.
 * - The teleports of `teleports.jsonc` are the teleport spells, the destinations of the teleport jewellery, and the home
 *   teleport, so that they are never written out by hand.
 * - The ships of `ships.jsonc` are the [ShipRoute]s, each boarded where its crew stands and left by its gangplank, with
 *   their fares and searches as requirements, so that they are never written out by hand either. Their ports are nodes
 *   that the walk edges link like the others.
 * - The banks of `banks.jsonc` are the bank booths at the bank anchors of the zones, each with the tile in front of it, on the
 *   side of the counter that customers use. They are not waypoints, because nothing is walked through them: they are where a
 *   trip to the nearest bank ends. Each is linked to the nodes nearest to it, which is where a trip walks the last stretch
 *   from. See [BankStands] and [BankLinks].
 * - The waypoints of `walk_graph.jsonc` are made from the zones and the sub-zones, moved onto a tile that can be stood
 *   on. The ones of `waypoints_manual.json`, which only the generator reads, are copied into it as they are.
 * - The walk edges of `walk_graph.jsonc` link every node of the graph (the ones from the files that are not generated here too)
 *   with the cost of walking between them, according to the collision of the world.
 *
 * It runs in two steps. [gather] needs the game thread, because it reads the world as it is. [finish] only reads the
 * collision snapshots, so it runs on a thread of its own, as it takes a while.
 *
 * @author Hydrozoa
 */
object WebWalkLiveGenerator {

    /**
     * The ladder out of the Port Phasmatys brewery's cellar, which isn't called a ladder.
     */
    private const val PHASMATYS_BREWERY_LADDER = 7433

    /**
     * The Barbarian Outpost agility course ladders, which are not for walking.
     */
    private val AGILITY_COURSE_LADDERS = setOf(Position(2532, 3545), Position(2532, 3545, 1))

    /**
     * How far from a seed, in tiles, a tile to stand on is looked for.
     */
    private const val SNAP_RADIUS = 4

    /**
     * The seed of the scattered waypoints, so that the same ones are made every time.
     */
    private const val SCATTER_SEED = 377L

    /**
     * The width and length of the cells that get a scattered waypoint. Waypoints of cells next to each other are less than twice this
     * far apart, which has to be inside the range of [RoutePathfinder] (56 tiles), so that the walks between them are
     * found without a long range search.
     */
    private const val SCATTER_CELL_SIZE = 16

    /**
     * How far apart, in tiles, nodes may be to be linked. Scattered waypoints are near the centres of their cells, so waypoints of
     * cells next to each other are usually about [SCATTER_CELL_SIZE] tiles apart, and this leaves room for a few that are farther
     * off, but not for the cells that are skipped. Every walk is well inside the range of [RoutePathfinder].
     */
    private const val NEAR_LINK_RADIUS = 25

    /**
     * How far from the stand of a bank, in tiles, nodes are looked for to link it to. This is inside the range of
     * [RoutePathfinder] like [NEAR_LINK_RADIUS], so the walks to the stand are found without a long range search.
     */
    private const val BANK_LINK_RADIUS = 25

    /**
     * How many nodes each bank is linked to, at most.
     */
    private const val BANK_LINK_COUNT = 6

    /**
     * The time of a teleport spell, in ticks: the cast is five ticks long (see `Magic.regularStyle`).
     */
    private const val SPELL_TICKS = 5

    /**
     * The time of a teleport by jewellery, in ticks: the same cast, and a tick to rub the jewellery and choose where to go.
     */
    private const val JEWELLERY_TICKS = 7

    /**
     * The time of the home teleport, in ticks.
     */
    private const val HOME_TICKS = 7

    /**
     * The time to use a cave or dungeon entrance, in ticks, on top of the delay of the [Entrance]: a tick to click it, and
     * one to be moved.
     */
    private const val ENTRANCE_TICKS = 2

    /**
     * How far, in tiles, from the departure of a ship its crew may stand.
     */
    private const val CREW_RADIUS = 8

    /**
     * How far, in tiles, from where a ship lands a player on its deck the gangplank that leaves it may be.
     */
    private const val PLANK_RADIUS = 8

    /**
     * The time to talk to the crew of a ship, in ticks, before the first option is shown.
     */
    private const val TALK_TICKS = 3

    /**
     * The time of each option of the boarding dialogue of a ship, in ticks, with the lines that come before it.
     */
    private const val BOARDING_OPTION_TICKS = 3

    /**
     * The time to walk to the gangplank of a ship and cross it, in ticks.
     */
    private const val PLANK_TICKS = 4

    /**
     * How many times a climb is made to find the lowest tile it arrives at, enough to find every tile of a small area.
     */
    private const val LANDING_SAMPLES = 200

    /**
     * The order that landing tiles are compared in.
     */
    private val LANDING_ORDER = compareBy<Position>({ it.z }, { it.x }, { it.y })

    /**
     * What [gather] found.
     *
     * @property climbs The ladders, stairs, trapdoors and entrances.
     * @property waypoints The waypoints.
     * @property teleports The teleports.
     * @property banks The bank booths, whose tiles to stand on are chosen by [finish].
     * @property ships The ships.
     * @property report What was made and what was left out.
     */
    class Gathered(val climbs: List<GeneratedObstacle>,
                   val waypoints: List<GeneratedWaypoint>,
                   val teleports: List<GeneratedTeleport>,
                   val banks: List<BankSeed>,
                   val ships: List<GeneratedShip>,
                   val report: GenerationReport)

    /**
     * The text of the generated files, without their [WebWalkWriter.header].
     *
     * @property obstacles The text of `obstacles.jsonc`.
     * @property climbs The text of `climbs.jsonc`.
     * @property crossings The text of `crossings.jsonc`.
     * @property teleports The text of `teleports.jsonc`.
     * @property banks The text of `banks.jsonc`.
     * @property walkGraph The text of `walk_graph.jsonc`.
     * @property ships The text of `ships.jsonc`.
     */
    class Output(val obstacles: String, val climbs: String, val crossings: String, val teleports: String,
                 val banks: String, val walkGraph: String, val ships: String)

    /**
     * Reads the world. Must be called on the game thread.
     *
     * @param directory The directory of the other files, which is `data/game/bots/webwalk`.
     * @return The ladders, stairs, trapdoors, entrances and waypoints.
     */
    fun gather(directory: Path): Gathered {
        val report = GenerationReport()
        val climbs = ArrayList<GeneratedObstacle>()
        val leavingPlanks = ArrayList<GameObject>()
        val trapdoors = HashMap<Int, Trapdoor>()
        for (trapdoor in Trapdoor.entries) {
            trapdoor.closed?.let { trapdoors[it] = trapdoor }
            trapdoors[trapdoor.open] = trapdoor
        }

        for (obj in world.objects.staticIterator()) {
            val def = obj.def() ?: continue
            when {
                // Entrances come first, because they are what the game does with their ids, whatever they are called.
                obj.id in Entrance.BY_ID -> entrance(obj, climbs, report)
                def.name == "Ladder" || def.id() == PHASMATYS_BREWERY_LADDER -> ladder(obj, climbs, report)
                def.name in StairDestination.NAMES -> stairs(obj, climbs, report)
                obj.id in trapdoors -> trapdoor(obj, trapdoors.getValue(obj.id), climbs, report)
                obj.id in ShipGangplanks.LEAVING_PLANKS -> leavingPlanks += obj
            }
        }
        report.count("climbs", climbs.size)
        report.count("entrances", climbs.count { it.type == EdgeType.ENTRANCE })
        val waypoints = waypoints(report)
        return Gathered(climbs, waypoints + scatter(waypoints + manualWaypoints(directory), report), teleports(report),
                        bankSeeds(report), ships(leavingPlanks, report), report)
    }

    /**
     * Makes a ship of each [ShipRoute]. It is boarded at the tile to stand on nearest the route's departure, which its crew
     * has to be near, and left by the gangplank nearest where it lands a player on its deck, at the foot of that gangplank.
     * Routes where one of those can't be found, or that have no [ShipBoarding], are left out and reported. Must be called on
     * the game thread.
     *
     * @param leavingPlanks The gangplanks of the map that lead ashore.
     */
    private fun ships(leavingPlanks: List<GameObject>, report: GenerationReport): List<GeneratedShip> {
        val ships = ArrayList<GeneratedShip>()
        for (route in ShipRoute.entries) {
            val what = "ship ${route.name}"
            val boarding = ShipBoarding.of(route)
            if (boarding == null) {
                report.skipped += "$what: no ShipBoarding says how to board it"
                continue
            }
            val departure = standableNear(route.departure)
            if (departure == null) {
                report.skipped += "$what from ${DoorObstacles.describe(route.departure)}: no tile to stand on nearby"
                continue
            }
            if (world.locator.findNpcs(departure, CREW_RADIUS) { it.id in route.crew }.isEmpty()) {
                report.skipped += "$what from ${DoorObstacles.describe(departure)}: none of its crew ${route.crew} is " +
                        "within $CREW_RADIUS tiles"
                continue
            }
            val deck = route.destination
            val plank = leavingPlanks.filter { it.position.computeLongestDistance(deck) <= PLANK_RADIUS }
                .minWithOrNull(compareBy({ it.position.computeLongestDistance(deck) }, { it.position.x }, { it.position.y }))
            if (plank == null) {
                report.skipped += "$what to ${DoorObstacles.describe(deck)}: no gangplank ashore within $PLANK_RADIUS tiles"
                continue
            }
            val landing = ShipGangplanks.landing(plank, deck.z, false)
            val arrival = if (LadderDestination.canLand(landing)) landing else standableNear(landing)
            if (arrival == null) {
                report.skipped += "$what: nothing to stand on at the foot of its gangplank, ${DoorObstacles.describe(landing)}"
                continue
            }
            val cost = TALK_TICKS + BOARDING_OPTION_TICKS * boarding.options.size + route.ticks + PLANK_TICKS
            val requirements = Requirements(coins = route.fare,
                                            flags = setOfNotNull(BotConditions.shipSearchFlag(route.search)))
            ships += GeneratedShip(route.name, departure, arrival, plank.id, plank.position, cost, requirements)
        }
        report.count("ships", ships.size)
        return ships
    }

    /**
     * Finds the bank booths at the bank anchors of the zones. Zones that share the anchors of another zone don't make them
     * twice. Anchors with no bank booth on them, or with no tile next to them to stand on, are left out and reported. Must be
     * called on the game thread.
     */
    private fun bankSeeds(report: GenerationReport): List<BankSeed> {
        val seeds = ArrayList<BankSeed>()
        val booths = HashSet<Position>()
        for (zone in Zone.entries) {
            zone.bankAnchors.forEachIndexed { index, booth ->
                if (!booths.add(booth)) {
                    return@forEachIndexed
                }
                val id = "${zone.name.lowercase()}_$index"
                if (world.locator.findObjectsOnTile(booth) { it.id in Banking.bankingObjects }.isEmpty()) {
                    report.skipped += "bank $id at ${DoorObstacles.describe(booth)}: no bank booth here"
                    return@forEachIndexed
                }
                val candidates = listOf(booth.translate(-1, 0), booth.translate(0, -1), booth.translate(0, 1), booth.translate(1, 0))
                    .filter { it.x >= 0 && it.y >= 0 && LadderDestination.canLand(it) }
                if (candidates.isEmpty()) {
                    report.skipped += "bank $id at ${DoorObstacles.describe(booth)}: no tile next to the booth to stand on"
                    return@forEachIndexed
                }
                seeds += BankSeed(id, booth, candidates)
            }
        }
        return seeds
    }

    /**
     * Makes the teleports: a spell for each of the teleport spells, an option for each destination of the teleport
     * jewellery, and the home teleport. Each lands on the nearest tile that can be stood on to its destination, and can be
     * used from as deep in the wilderness as the game allows: [TeleportJewellery.maxWildernessLevel] for jewellery, and
     * [TeleportAction.MAX_WILDERNESS_LEVEL] for the spells and the home teleport, which don't say otherwise. Must be called on
     * the game thread.
     */
    private fun teleports(report: GenerationReport): List<GeneratedTeleport> {
        val teleports = ArrayList<GeneratedTeleport>()
        fun add(id: String, kind: TeleportKind, key: String?, option: Int?, destination: Position, cost: Int,
                maxWilderness: Int = TeleportAction.MAX_WILDERNESS_LEVEL) {
            val dest = standableNear(destination)
            if (dest == null) {
                report.skipped += "teleport $id to ${DoorObstacles.describe(destination)}: no tile to stand on nearby"
                return
            }
            teleports += GeneratedTeleport(id, kind, key, option, dest, cost, maxWilderness)
        }
        for (spell in TeleportSpell.entries) {
            add("spell_${spell.name.lowercase()}", TeleportKind.SPELL, spell.name, null, spell.destination, SPELL_TICKS)
        }
        for (jewellery in TeleportJewellery.entries) {
            jewellery.destinations.forEachIndexed { index, destination ->
                add("jewellery_${jewellery.name.lowercase()}_${index + 1}", TeleportKind.JEWELLERY, jewellery.name,
                    index + 1, destination.centre, JEWELLERY_TICKS, jewellery.maxWildernessLevel)
            }
        }
        add("home", TeleportKind.HOME, null, null, Luna.settings().game().startingPosition(), HOME_TICKS)
        report.count("teleports", teleports.size)
        return teleports
    }

    /**
     * Makes the obstacles of a ladder.
     */
    private fun ladder(obj: GameObject, climbs: MutableList<GeneratedObstacle>, report: GenerationReport) {
        if (obj.position in AGILITY_COURSE_LADDERS) {
            report.skipped += "ladder ${obj.id} at ${DoorObstacles.describe(obj.position)}: agility course ladder"
            return
        }
        val refusal = LadderType.forObject(obj).refusal
        if (refusal != null) {
            report.skipped += "ladder ${obj.id} at ${DoorObstacles.describe(obj.position)}: $refusal"
            return
        }
        climb(EdgeType.LADDER, obj, StairDestination.directions(obj), climbs, report) { from, up ->
            LadderDestination.destination(obj, from, up)
        }
    }

    /**
     * Makes the obstacles of a staircase.
     */
    private fun stairs(obj: GameObject, climbs: MutableList<GeneratedObstacle>, report: GenerationReport) {
        climb(EdgeType.STAIR, obj, StairDestination.directions(obj), climbs, report) { from, up ->
            StairDestination.destination(obj, from, up)
        }
    }

    /**
     * Makes the obstacle of a trapdoor, which only goes down.
     */
    private fun trapdoor(obj: GameObject, trapdoor: Trapdoor, climbs: MutableList<GeneratedObstacle>,
                         report: GenerationReport) {
        for (from in climbingTiles(obj)) {
            val to = trapdoor.landing.from(obj.position, from)?.let { TrapdoorLanding.land(it) }
            if (to != null && to != from) {
                // A trapdoor has to be opened before it is climbed through.
                val option = if (obj.id == trapdoor.open) "Climb-down" else "Open"
                climbs += GeneratedObstacle(EdgeType.TRAPDOOR, obj.id, obj.position, from, to, option)
                return
            }
        }
        report.skipped += "trapdoor ${obj.id} at ${DoorObstacles.describe(obj.position)}: nowhere to land"
    }

    /**
     * Makes the obstacles of a cave or dungeon entrance, one for each way through it. Entrances that land somewhere different
     * from each tile that they are used from, such as the ones that move players a set distance, would otherwise make one
     * for every tile, so the tiles that land the same way are made into one: the one that moves the farthest, which for
     * entrances that cross to the other side of a line is the tile on the far side.
     */
    private fun entrance(obj: GameObject, climbs: MutableList<GeneratedObstacle>, report: GenerationReport) {
        val what = "entrance ${obj.id} at ${DoorObstacles.describe(obj.position)}"
        val entrance = Entrance.forObject(obj.id, obj.position)
        if (entrance == null) {
            report.skipped += "$what: no entrance here"
            return
        }
        // Entrances are all used with their first option, see dungeonEntrances.kts.
        val option = obj.def().actions.getOrNull(0)
        if (option == null) {
            report.skipped += "$what: no option to use it with"
            return
        }

        val crossings = ArrayList<Pair<Position, Position>>()
        var unstandable: Position? = null
        for (from in climbingTiles(obj)) {
            val to = entrance.landing.from(from)
            if (to == from) {
                continue
            }
            if (LadderDestination.canLand(to)) {
                crossings += Pair(from, to)
            } else if (unstandable == null) {
                unstandable = to
            }
        }
        val ways = ArrayList<Pair<Position, Position>>()
        val farthestFirst = compareByDescending<Pair<Position, Position>> { it.first.computeLongestDistance(it.second) }
            .thenComparing({ it.first }, LANDING_ORDER)
        for (crossing in crossings.sortedWith(farthestFirst)) {
            if (ways.none { sameWay(it, crossing) }) {
                ways += crossing
            }
        }
        if (ways.isEmpty()) {
            val landing = unstandable?.let { ", ${DoorObstacles.describe(it)} can't be stood on" } ?: ""
            report.skipped += "$what: nowhere to land$landing"
            return
        }
        val cost = ENTRANCE_TICKS + entrance.delay + if (entrance.arriveDelay) 1 else 0
        for ((from, to) in ways) {
            climbs += GeneratedObstacle(EdgeType.ENTRANCE, obj.id, obj.position, from, to, option, cost = cost)
        }
    }

    /**
     * Determines if two crossings of an entrance go the same way: between the same planes, in directions that are no
     * more than a right angle apart.
     */
    private fun sameWay(first: Pair<Position, Position>, second: Pair<Position, Position>): Boolean {
        val (firstFrom, firstTo) = first
        val (secondFrom, secondTo) = second
        if (firstFrom.z != secondFrom.z || firstTo.z != secondTo.z) {
            return false
        }
        val dot = (firstTo.x - firstFrom.x) * (secondTo.x - secondFrom.x) +
                (firstTo.y - firstFrom.y) * (secondTo.y - secondFrom.y)
        return dot >= 0
    }

    /**
     * Makes an obstacle for each of the directions that an object can be climbed in, from the first tile beside it that it
     * can be climbed from. The same crossing is never made twice, because some objects ignore the direction.
     */
    private fun climb(type: EdgeType, obj: GameObject, directions: Set<Boolean>, climbs: MutableList<GeneratedObstacle>,
                      report: GenerationReport, destination: (Position, Boolean) -> Position?) {
        val tiles = climbingTiles(obj)
        if (directions.isEmpty()) {
            report.skipped += "${type.name.lowercase()} ${obj.id} at ${DoorObstacles.describe(obj.position)}: " +
                    "no way to climb it"
            return
        }
        val made = HashSet<Pair<Position, Position>>()
        for (up in directions.sortedDescending()) {
            var found = false
            for (from in tiles) {
                val to = canonical { destination(from, up) }
                if (to != null && to != from) {
                    if (made.add(Pair(from, to))) {
                        climbs += GeneratedObstacle(type, obj.id, obj.position, from, to, optionFor(obj, up))
                    }
                    found = true
                    break
                }
            }
            if (!found) {
                report.skipped += "${type.name.lowercase()} ${obj.id} at ${DoorObstacles.describe(obj.position)}: " +
                        "nowhere to land when climbing ${if (up) "up" else "down"}"
            }
        }
    }

    /**
     * Finds where a climb arrives. Some stairs land players on a random tile of an area, as they did in the original game,
     * so the climb is made many times and the lowest tile that it arrives at is used, which keeps the data the same every
     * time it is generated.
     */
    private fun canonical(destination: () -> Position?): Position? {
        var lowest: Position? = null
        repeat(LANDING_SAMPLES) {
            val landing = destination() ?: return null
            if (lowest == null || LANDING_ORDER.compare(landing, lowest) < 0) {
                lowest = landing
            }
        }
        return lowest
    }

    /**
     * Finds the menu option that climbs an object in a direction: the option of that direction if the object has one, and
     * otherwise "Climb", which asks which way.
     */
    private fun optionFor(obj: GameObject, up: Boolean): String {
        val names = if (up) setOf("climb-up", "walk-up", "ascend") else setOf("climb-down", "walk-down", "descend")
        return obj.def().actions.filterNotNull().firstOrNull { it.lowercase() in names } ?: "Climb"
    }

    /**
     * Finds the tiles that an object can be climbed from, in the same order every time.
     */
    private fun climbingTiles(obj: GameObject) =
        LadderDestination.climbingTiles(obj).sortedWith(compareBy({ it.z }, { it.x }, { it.y }))

    /**
     * Reads the waypoints of `waypoints_manual.json`, which [finish] copies into the graph as they are, so that [scatter] can leave
     * the cells that they are in alone. There are none if the file doesn't exist.
     */
    private fun manualWaypoints(directory: Path): List<GeneratedWaypoint> {
        val path = directory.resolve(WebWalkLoader.WAYPOINTS_MANUAL)
        if (!Files.exists(path)) {
            return emptyList()
        }
        return WebWalkLoader.fromSources(mapOf(WebWalkLoader.WAYPOINTS_MANUAL to Files.readString(path))).nodes.values
            .filter { it.kind == NodeKind.WAYPOINT }.map { GeneratedWaypoint(it.id, it.position, it.tags) }
    }

    /**
     * Spreads waypoints over the overworld, which is the ground floor above the dungeons, so that no walk of the graph is longer
     * than the range of the route pathfinder. Cells that already have a waypoint, which [waypoints] are, are left alone.
     */
    private fun scatter(waypoints: List<GeneratedWaypoint>, report: GenerationReport): List<GeneratedWaypoint> {
        val table = ctx.cache.mapIndexTable
        val regions = table.allRegions.map { it.absPosition }.filter { it.y < LadderType.CELLAR_OFFSET }
        if (regions.isEmpty()) {
            return emptyList()
        }
        val occupied = waypoints.filter { it.pos.z == 0 }
            .map { Pair(it.pos.x / SCATTER_CELL_SIZE, it.pos.y / SCATTER_CELL_SIZE) }.toSet()
        val scattered = ScatterWaypoints.generate(SCATTER_SEED, SCATTER_CELL_SIZE,
                                             regions.minOf { it.x }, regions.minOf { it.y },
                                             regions.maxOf { it.x } + 63, regions.maxOf { it.y } + 63, 0, occupied) {
            table.indexTable.containsKey(it.region) && LadderDestination.canLand(it)
        }
        report.count("scattered waypoints", scattered.size)
        return scattered
    }

    /**
     * Makes the waypoints of the zones and the sub-zones.
     */
    private fun waypoints(report: GenerationReport): List<GeneratedWaypoint> {
        val seeds = ArrayList<WaypointSeed>()
        for (zone in Zone.entries) {
            seeds += WaypointSeed("zone_${zone.name.lowercase()}", zone.anchor, setOf("zone"))
        }
        for (subZone in SubZone.entries) {
            seeds += WaypointSeed("subzone_${subZone.name.lowercase()}", subZone.inside, setOf("subzone"))
        }
        return WaypointSeeds.generate(seeds, ::standableNear, report)
    }

    /**
     * Finds the tile to stand on that is nearest a position, which is the position if it can be stood on.
     */
    private fun standableNear(position: Position): Position? {
        for (radius in 0..SNAP_RADIUS) {
            val ring = ArrayList<Position>()
            for (x in -radius..radius) {
                for (y in -radius..radius) {
                    if (maxOf(Math.abs(x), Math.abs(y)) == radius) {
                        ring += position.translate(x, y)
                    }
                }
            }
            ring.sortWith(compareBy({ it.x }, { it.y }))
            ring.firstOrNull { it.x >= 0 && it.y >= 0 && LadderDestination.canLand(it) }?.let { return it }
        }
        return null
    }

    /**
     * Makes the text of the generated files, with the walk edges between all the nodes. Throws [WebWalkDataException] if
     * the files of [directory], with what was gathered, don't make a valid graph.
     *
     * @param gathered What [gather] found.
     * @param directory The directory of the other files, which is `data/game/bots/webwalk`.
     * @return The text of `climbs.jsonc` and `walk_graph.jsonc`.
     */
    fun finish(gathered: Gathered, directory: Path): Output {
        val sources = HashMap<String, String>()
        for (file in WebWalkLoader.FILES + WebWalkLoader.WAYPOINTS_MANUAL) {
            val path = directory.resolve(file)
            if (Files.exists(path)) {
                sources[file] = Files.readString(path)
            }
        }
        val doors = WebWalkDoorGenerator.generate(Path.of("data", "game", "world", "doors"), ctx.cache.mapIndexTable)
        doors.report.counts.forEach { (what, amount) -> gathered.report.count(what, amount) }
        gathered.report.skipped += doors.report.skipped
        sources[WebWalkLoader.OBSTACLES] = doors.obstacles
        val climbsText = WebWalkWriter.obstacles(gathered.climbs)
        sources[WebWalkLoader.CLIMBS] = climbsText
        val crossings = WebWalkCrossingGenerator.generate(ctx.cache.mapIndexTable)
        crossings.report.counts.forEach { (what, amount) -> gathered.report.count(what, amount) }
        gathered.report.skipped += crossings.report.skipped
        sources[WebWalkLoader.CROSSINGS] = crossings.text
        val teleportsText = WebWalkWriter.teleports(gathered.teleports)
        sources[WebWalkLoader.TELEPORTS] = teleportsText
        // The ports are nodes like any other, so they are linked to the walk graph below.
        val shipsText = WebWalkWriter.ships(gathered.ships)
        sources[WebWalkLoader.SHIPS] = shipsText
        // The banks are chosen further down, from how the nodes are linked, so the ones of an earlier run are left out.
        sources.remove(WebWalkLoader.BANKS)
        sources[WebWalkLoader.WALK_GRAPH] = WebWalkWriter.walkGraph(gathered.waypoints, emptyList())

        val graph = WebWalkLoader.fromSources(sources)
        val collision = world.collisionManager
        val doorCrossings = DoorCrossings.of(graph)
        val estimator = PathfinderWalkEstimator(collision, longRange = false, doors = doorCrossings)

        // Which nodes can be walked between is found once for all of them, so the pathfinder is only asked about the ones that
        // can. Otherwise every search for a node that can't be reached would cover everything that can.
        val view = collision.view(true)
        val nodes = graph.nodes.values.toList()
        // The tiles beside the bank booths are labelled too, to tell which side of the counter is where customers stand.
        val bankTiles = gathered.banks.flatMap { it.candidates }.distinct()
        val allLabels = WalkableComponents { x, y, z, dx, dy ->
            StepValidator.canTravel(view, z, x, y, dx, dy, 1, 0, RouteStrategy.NORMAL)
        }.label(nodes.map { it.position } + bankTiles)
        val labels = allLabels.copyOfRange(0, nodes.size)
        val bankTileLabels = bankTiles.indices.associate { bankTiles[it] to allLabels[nodes.size + it] }
        gathered.report.count("walkable areas", labels.toSet().size)

        // A scattered waypoint in an area that has no other node can't be walked to from anywhere that matters, so it is left out.
        val anchored = nodes.indices.filter { ScatterWaypoints.TAG !in nodes[it].tags }.map { labels[it] }.toSet()
        val dropped = nodes.indices.filter { ScatterWaypoints.TAG in nodes[it].tags && labels[it] !in anchored }
            .map { nodes[it].id }.toSet()
        gathered.report.count("scattered waypoints left out", dropped.size)
        val componentById = nodes.indices.associate { nodes[it].id to labels[it] }

        // Nodes should be close together, so the walks between them stay inside the range of the route pathfinder. A
        // scattered waypoint with no walk to a node in range is only in a pocket that long walks lead to, so it is left out too,
        // and the rest are linked again without it, until none are left that have to be.
        val pathfinderCost = estimator::ticks
        var kept = nodes.filter { it.id !in dropped }
        var links: List<GeneratedLink>
        var isolated = 0
        while (true) {
            links = WalkLinker(pathfinderCost, nearRadius = NEAR_LINK_RADIUS, farRadius = NEAR_LINK_RADIUS, doors = doorCrossings)
                .link(kept) { componentById.getValue(it.id) }
            val byId = kept.associateBy { it.id }
            val inRange = HashSet<String>()
            for (link in links) {
                if (byId.getValue(link.from).position.computeLongestDistance(byId.getValue(link.to).position) <=
                    NEAR_LINK_RADIUS) {
                    inRange += link.from
                    inRange += link.to
                }
            }
            val pruned = kept.filter { ScatterWaypoints.TAG in it.tags && it.id !in inRange }.map { it.id }.toSet()
            if (pruned.isEmpty()) {
                break
            }
            isolated += pruned.size
            kept = kept.filter { it.id !in pruned }
        }
        gathered.report.count("scattered waypoints left out (nothing in range)", isolated)
        val keptIds = kept.map { it.id }.toSet()
        val generatedIds = gathered.waypoints.map { it.id }.toSet()
        val manual = kept.filter { it.kind == NodeKind.WAYPOINT && it.id !in generatedIds }
            .map { GeneratedWaypoint(it.id, it.position, it.tags) }
        gathered.report.count("manual waypoints", manual.size)
        val waypoints = gathered.waypoints.filter { it.id in keptIds } + manual
        gathered.report.count("nodes", kept.size)
        gathered.report.count("walk links", links.size)

        // The customers' side of a counter is in an area with nodes in it, and the banker's side is closed off from them.
        val nodesByArea = kept.groupingBy { componentById.getValue(it.id) }.eachCount()
        val stands = BankStands.generate(gathered.banks, { nodesByArea[bankTileLabels.getValue(it)] ?: 0 }, gathered.report)
        // The nodes that a stand can be walked from are the ones in its area, so the others are not tried. The links are walked
        // by the same estimator as the links between nodes, from the node to the stand.
        val banks = BankLinks.generate(stands, kept, { node, stand -> componentById.getValue(node.id) == bankTileLabels.getValue(stand) },
                                       pathfinderCost, BANK_LINK_RADIUS, BANK_LINK_COUNT, gathered.report)
        val banksText = WebWalkWriter.banks(banks)
        val walkGraphText = WebWalkWriter.walkGraph(waypoints, links)
        // Checks the files as they will be loaded: the links of the banks are to nodes of the walk graph. The manual waypoints
        // are already in the walk graph, so they are not read again.
        WebWalkLoader.fromSources(sources - WebWalkLoader.WAYPOINTS_MANUAL +
                                          mapOf(WebWalkLoader.BANKS to banksText, WebWalkLoader.WALK_GRAPH to walkGraphText))

        return Output(doors.obstacles, climbsText, crossings.text, teleportsText, banksText, walkGraphText, shipsText)
    }

    /**
     * The names of the generated files, which are all written by [write] and which [missingFiles] looks for.
     */
    val GENERATED_FILES = listOf(WebWalkLoader.OBSTACLES, WebWalkLoader.CLIMBS, WebWalkLoader.CROSSINGS,
                                 WebWalkLoader.TELEPORTS, WebWalkLoader.BANKS, WebWalkLoader.WALK_GRAPH,
                                 WebWalkLoader.SHIPS)

    /**
     * Finds the generated files that don't exist. The files depend on each other (the banks are linked to nodes of the walk
     * graph, for one), so if any is missing they should all be generated again.
     *
     * @param directory The directory of the files.
     * @return The names of the files that are missing.
     */
    fun missingFiles(directory: Path): List<String> = GENERATED_FILES.filter { !Files.exists(directory.resolve(it)) }

    /**
     * Pairs each generated file with the text that it should have, without its header.
     */
    private fun texts(output: Output) = mapOf(WebWalkLoader.OBSTACLES to output.obstacles,
                                              WebWalkLoader.CLIMBS to output.climbs,
                                              WebWalkLoader.CROSSINGS to output.crossings,
                                              WebWalkLoader.TELEPORTS to output.teleports,
                                              WebWalkLoader.BANKS to output.banks,
                                              WebWalkLoader.WALK_GRAPH to output.walkGraph,
                                              WebWalkLoader.SHIPS to output.ships)

    /**
     * Writes the generated files, each with a [WebWalkWriter.header] that says it is generated and when. Every file is written
     * to a temporary file first and only moved into place once all of them are written, so a failure half way never leaves a
     * set of files that [missingFiles] would think is complete.
     *
     * @param output The text to write.
     * @param directory The directory to write to.
     * @param generatedAt When the files were generated.
     */
    fun write(output: Output, directory: Path, generatedAt: Instant = Instant.now()) {
        val header = WebWalkWriter.header(generatedAt)
        Files.createDirectories(directory)
        val temporary = texts(output).keys.associateWith { directory.resolve("$it.tmp") }
        try {
            for ((file, text) in texts(output)) {
                Files.writeString(temporary.getValue(file), header + text)
            }
            for ((file, tmp) in temporary) {
                Files.move(tmp, directory.resolve(file), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.values.forEach { Files.deleteIfExists(it) }
        }
    }

    /**
     * Finds the generated files that are not what [output] says they should be. The header of a file is left out of the
     * comparison, as it changes every time.
     *
     * @param output The text that the files should have.
     * @param directory The directory of the files.
     * @return The names of the files that are different or missing.
     */
    fun outOfDate(output: Output, directory: Path): List<String> {
        val stale = ArrayList<String>()
        for ((file, text) in texts(output)) {
            val path = directory.resolve(file)
            if (!Files.exists(path)) {
                stale += "$file (missing)"
                continue
            }
            val found = WebWalkWriter.withoutHeader(Files.readString(path).replace("\r\n", "\n"))
            if (found != text) {
                stale += "$file (${firstDifference(text, found)})"
            }
        }
        return stale
    }

    /**
     * Describes the first line where two texts differ, for the report.
     */
    private fun firstDifference(expected: String, found: String): String {
        val expectedLines = expected.lines()
        val foundLines = found.lines()
        for (index in 0 until maxOf(expectedLines.size, foundLines.size)) {
            val a = expectedLines.getOrNull(index)
            val b = foundLines.getOrNull(index)
            if (a != b) {
                return "line ${index + 1}: generated '${a?.trim()}' but the file has '${b?.trim()}'; " +
                        "${expectedLines.size} lines generated, ${foundLines.size} in the file"
            }
        }
        return "no differing line"
    }
}

/**
 * Runs [WebWalkLiveGenerator]. It is run by the `generateWebWalk` Gradle task, which exits the server when it is done, and
 * when the server starts without the generated files, which it then goes on running with.
 *
 * @author Hydrozoa
 */
object WebWalkGenerationRunner {

    /**
     * The directory of the files.
     */
    private val DIRECTORY = Path.of("data", "game", "bots", "webwalk")

    /**
     * Logs a line of the generation.
     */
    private fun say(line: String) = logger.info("[webwalk] {}", line)

    /**
     * Finds the generated files that don't exist, see [WebWalkLiveGenerator.missingFiles].
     *
     * @return The names of the files that are missing.
     */
    fun missingFiles(): List<String> = WebWalkLiveGenerator.missingFiles(DIRECTORY)

    /**
     * Gathers on the game thread (so this must be called from it), then finishes on a thread of its own. The server exits
     * when it is done, with an exit code of `1` if anything went wrong.
     *
     * @param check `true` to only report the files that are out of date instead of writing them.
     */
    fun run(check: Boolean) {
        val gathered = gather()
        if (gathered == null) {
            System.exit(1)
            return
        }
        Thread({ System.exit(if (finish(gathered, check)) 0 else 1) }, "WebWalkGeneratorThread").start()
    }

    /**
     * Generates the files when the server starts without them. Gathers on the game thread (so this must be called from it),
     * then finishes on [executor]. It is meant for the task pool of a [io.luna.game.event.impl.ServerStateChangedEvent], which
     * the server waits for before it goes online. The server doesn't exit, and if anything goes wrong it is logged and the
     * files are left as they were, so that bots can't web-walk until the problem is fixed.
     *
     * @param executor Where the part that takes a while is run.
     */
    fun generateMissing(executor: Executor) {
        val gathered = gather() ?: return
        executor.execute { finish(gathered, check = false) }
    }

    /**
     * Reads the world, see [WebWalkLiveGenerator.gather].
     *
     * @return What was found, or `null` if it failed.
     */
    private fun gather(): WebWalkLiveGenerator.Gathered? {
        val gathered = try {
            WebWalkLiveGenerator.gather(DIRECTORY)
        } catch (e: Exception) {
            logger.error("Could not gather the web-walker data!", e)
            return null
        }
        say("Gathered ${gathered.climbs.size} climbs and ${gathered.waypoints.size} waypoints. Linking, which takes a while...")
        return gathered
    }

    /**
     * Links what was gathered and then writes the files, or reports which are out of date.
     *
     * @param gathered What [gather] found.
     * @param check `true` to only report the files that are out of date instead of writing them.
     * @return `true` if it worked.
     */
    private fun finish(gathered: WebWalkLiveGenerator.Gathered, check: Boolean): Boolean {
        try {
            val output = WebWalkLiveGenerator.finish(gathered, DIRECTORY)
            gathered.report.summary().forEach { say(it) }
            if (check) {
                val stale = WebWalkLiveGenerator.outOfDate(output, DIRECTORY)
                say(if (stale.isEmpty()) "The generated files are up to date." else "Out of date: $stale")
            } else {
                WebWalkLiveGenerator.write(output, DIRECTORY)
                say("Wrote ${WebWalkLiveGenerator.GENERATED_FILES.joinToString()} to $DIRECTORY.")
            }
            return true
        } catch (e: Throwable) {
            logger.error("Could not generate the web-walker data!", e)
            return false
        }
    }
}
