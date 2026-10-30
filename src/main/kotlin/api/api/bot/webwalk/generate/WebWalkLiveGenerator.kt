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
import api.bot.webwalk.walk.BankWithdrawal
import api.bot.webwalk.walk.crossing.AltarCrossing
import api.bot.webwalk.walk.crossing.JarvaldCrossing
import api.bot.webwalk.walk.crossing.WaterbirthCrossing
import api.bot.webwalk.walk.crossing.WaterbirthPartyCrossing
import game.content.waterbirth.Jarvald
import game.content.waterbirth.Waterbirth
import api.bot.webwalk.walk.crossing.RopeEntranceCrossing
import api.bot.webwalk.walk.crossing.EssenceMineCrossing
import game.skill.runecrafting.essenceMine.EssenceMine
import game.skill.runecrafting.essenceMine.EssenceMineWizard
import game.obj.wildernessLevers.WildernessLever
import game.skill.runecrafting.enterAltar.Altar
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
import engine.obj.Hatch
import engine.obj.LadderDestination
import engine.obj.LadderType
import engine.obj.StairDestination
import engine.obj.TrapdoorLanding
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.interact.InteractionPolicy
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
 * Everything is made in a single run of the server, because most of it needs a running world. The server makes it every time
 * that it starts, see [WebWalkGenerationRunner]. The files start with a comment that says they are generated and when, see
 * [WebWalkWriter.header].
 *
 * - The closed doors, gates and curtains of `obstacles.jsonc` are made by [WebWalkDoorGenerator] from the map data of the
 *   cache.
 * - The crossings of `crossings.jsonc` are the objects of the [SpecialCrossings] that the cache's map data places, such as the
 *   leaves of the Al Kharid toll gate, or the fixed tiles of their placements, such as the H.A.M. Hideout trapdoor, made by
 *   [WebWalkCrossingGenerator].
 * - The ladders, stairs, trapdoors and entrances of `climbs.jsonc` land where [LadderDestination], [StairDestination],
 *   [Hatch] and [Entrance] say that they do, which is what players get.
 * - The teleports of `teleports.jsonc` are the teleport spells, the destinations of the teleport jewellery, and the home
 *   teleport, so that they are never written out by hand.
 * - The ships of `ships.jsonc` are the [ShipRoute]s, each boarded where its crew stands and left by its gangplank, with
 *   their fares and searches as requirements, so that they are never written out by hand either. Their ports are nodes
 *   that the walk edges link like the others.
 * - The banks of `banks.jsonc` are the booths or registered bankers at the bank anchors of the zones, each with a reachable
 *   customer stand. They are not waypoints, because nothing is walked through them: they are where a
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
     * @property banks The bank destinations, whose tiles to stand on are chosen by [finish].
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
        val trapdoors = HashMap<Int, Hatch>()
        for (trapdoor in Hatch.ALL) {
            trapdoor.closed?.let { trapdoors[it] = trapdoor }
            trapdoors[trapdoor.open] = trapdoor
        }

        for (obj in world.objects.staticIterator()) {
            val def = obj.def() ?: continue
            when {
                // Entrances come first, because they are what the game does with their ids, whatever they are called.
                Waterbirth.Gate.entries.any { it.id == obj.id && it.position == obj.position } ||
                    obj.id == Waterbirth.BLOCKER && Waterbirth.support(obj.position) != null -> waterbirth(obj, climbs, report)
                obj.id in Altar.OBJECT_TO_ALTAR || obj.id in Altar.PORTAL_TO_ALTAR -> altar(obj, climbs, report)
                obj.id == EssenceMine.PORTAL -> essencePortal(obj, climbs, report)
                obj.id == WildernessLever.LAVA_MAZE_DUNGEON.id -> lever(obj, WildernessLever.LAVA_MAZE_DUNGEON, climbs, report)
                obj.id == WildernessLever.KING_BLACK_DRAGON_LAIR.id -> lever(obj, WildernessLever.KING_BLACK_DRAGON_LAIR, climbs, report)
                obj.id in Entrance.BY_ID || obj.id in RopeEntranceCrossing.entrances -> entrance(obj, climbs, report)
                def.name == "Ladder" || def.id() == PHASMATYS_BREWERY_LADDER -> ladder(obj, climbs, report)
                def.name in StairDestination.NAMES -> stairs(obj, climbs, report)
                obj.id in trapdoors -> trapdoor(obj, trapdoors.getValue(obj.id), climbs, report)
                obj.id in ShipGangplanks.LEAVING_PLANKS -> leavingPlanks += obj
            }
        }
        essenceWizards(climbs, report)
        jarvald(climbs, report)
        waterbirthParty(climbs, report)
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
     * Finds bank objects or registered bankers at the zones' bank anchors, without duplicating shared anchors.
     * Missing destinations and destinations without a reachable stand are left out and reported.
     * Must be called on the game thread.
     */
    private fun bankSeeds(report: GenerationReport): List<BankSeed> {
        val seeds = ArrayList<BankSeed>()
        val anchors = HashSet<Position>()
        for (zone in Zone.entries) {
            zone.bankAnchors.forEachIndexed { index, booth ->
                if (!anchors.add(booth)) {
                    return@forEachIndexed
                }
                val id = "${zone.name.lowercase()}_$index"
                bankSeed(id, booth, report)?.let { seeds += it }
            }
        }
        return seeds
    }

    /**
     * Snapshots one anchored bank on the game thread. Existing bank objects take precedence over bankers.
     * NPC candidates must pass the same reach policy as the native second-click banking handler. Customer-side
     * access is then selected by ordinary walk connectivity; only ids and positions leave this method.
     * A banker already away from its spawn anchor is reported instead of generating a stale customer stand.
     *
     * @param id The generated bank id.
     * @param anchor The zone's bank anchor, or a banker's exact spawn position.
     * @param report Where unavailable destinations are reported.
     * @param canStand Whether a candidate has floor and can be walked onto.
     * @param bankerIds The registered native second-click banking NPC ids.
     * @param world The world to inspect.
     * @return An immutable seed, or `null` when this anchor cannot currently be used.
     */
    internal fun bankSeed(id: String, anchor: Position, report: GenerationReport,
                          canStand: (Position) -> Boolean = LadderDestination::canLand,
                          bankerIds: Set<Int> = Banking.bankingNpcs,
                          world: World = api.predef.world): BankSeed? {
        val bankObject = world.locator.findObjectsOnTile(anchor) { it.id in Banking.bankingObjects }.isNotEmpty()
        val bankers = if (bankObject) emptyList() else world.npcs.filter {
            it.basePosition == anchor && it.position == anchor && it.id in bankerIds &&
                    it.state == EntityState.ACTIVE && it.isAlive
        }
        val banker = bankers.firstOrNull { BankWithdrawal.isBanker(it, bankerIds) }
        val what = "bank $id at ${DoorObstacles.describe(anchor)}"
        if (!bankObject && banker == null) {
            val reason = if (bankers.isEmpty()) "no bank object or active registered banker at this anchor" else
                "registered banker has no direct Bank action"
            report.skipped += "$what: $reason"
            return null
        }
        val candidates = listOf(anchor.translate(-1, 0), anchor.translate(0, -1), anchor.translate(0, 1), anchor.translate(1, 0))
            .filter { it.x >= 0 && it.y >= 0 && canStand(it) &&
                    (banker == null || world.collisionManager.reached(it, banker, Banking.BANKER_POLICY)) }
        if (candidates.isEmpty()) {
            report.skipped += "$what: no reachable customer stand next to the ${if (bankObject) "bank object" else "banker"}"
            return null
        }
        return BankSeed(id, anchor, candidates, npcId = banker?.id)
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
     * Makes the obstacle of a trapdoor or manhole, which only goes down.
     */
    private fun trapdoor(obj: GameObject, trapdoor: Hatch, climbs: MutableList<GeneratedObstacle>,
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
        val ropeId = RopeEntranceCrossing.entrances.entries.firstOrNull { obj.id == it.key || obj.id == it.value }?.key
        val entrance = Entrance.forObject(ropeId?.let { RopeEntranceCrossing.entrances.getValue(it) } ?: obj.id, obj.position)
        if (entrance == null) {
            report.skipped += "$what: no entrance here"
            return
        }
        // Entrances are all used with their first option, see dungeonEntrances.kts.
        val option = if (ropeId != null) "Climb-down" else obj.def().actions.getOrNull(0)
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
            climbs += if (ropeId == null) {
                GeneratedObstacle(EdgeType.ENTRANCE, obj.id, obj.position, from, to, option, cost = cost)
            } else {
                GeneratedObstacle(EdgeType.CROSSING, ropeId, obj.position, from, to,
                    handler = RopeEntranceCrossing.ID, cost = cost + 2,
                    requirements = RopeEntranceCrossing.requirements(ropeId), bidirectional = false)
            }
        }
    }

    /**
     * Links ruins and portals to their scripted destinations. Entry requirements never apply to an exit.
     */
    private fun altar(obj: GameObject, crossings: MutableList<GeneratedObstacle>, report: GenerationReport) {
        val entry = Altar.OBJECT_TO_ALTAR[obj.id]
        val altar = entry ?: Altar.PORTAL_TO_ALTAR.getValue(obj.id)
        val to = if (entry != null) altar.enter else altar.exit
        val from = climbingTiles(obj).firstOrNull()
        if (from == null || !LadderDestination.canLand(to)) {
            report.skipped += "altar ${obj.id} at ${obj.position}: no interaction tile or landing"
            return
        }
        crossings += GeneratedObstacle(EdgeType.CROSSING, obj.id, obj.position, from, to,
            handler = AltarCrossing.ID, cost = if (entry != null) 5 else 2,
            requirements = if (entry != null) Requirements(flags = setOf(AltarCrossing.flag(altar))) else Requirements.NONE,
            bidirectional = false)
        report.count("altar crossings")
    }

    /**
     * Adds the five NPC teleport shortcuts; their handlers normalize random mine landings to the arrival node.
     */
    private fun essenceWizards(crossings: MutableList<GeneratedObstacle>, report: GenerationReport) {
        val to = standableNear(SubZone.ESSENCE_MINE.inside)
        if (to == null) {
            report.skipped += "essence mine: no arrival node"
            return
        }
        for (wizard in EssenceMineWizard.entries) {
            val npc = world.locator.findNpcs(wizard.exit, 16) { it.id == wizard.npcId }.firstOrNull()
            val from = standableNear(wizard.exit)
            if (npc == null || from == null) {
                report.skipped += "essence wizard ${wizard.name}: missing NPC or departure tile"
                continue
            }
            crossings += GeneratedObstacle(EdgeType.CROSSING, null, npc.position, from, to,
                handler = EssenceMineCrossing.ID, cost = 12, bidirectional = false, npcId = wizard.npcId)
            report.count("essence wizard crossings")
        }
    }

    /**
     * Adds each possible saved return destination, gated so the planner cannot use the mine to travel between wizards.
     */
    private fun essencePortal(obj: GameObject, crossings: MutableList<GeneratedObstacle>, report: GenerationReport) {
        val from = climbingTiles(obj).firstOrNull()
        if (from == null) {
            report.skipped += "essence portal at ${obj.position}: no interaction tile"
            return
        }
        for (wizard in EssenceMineWizard.entries) {
            crossings += GeneratedObstacle(EdgeType.CROSSING, obj.id, obj.position, from, wizard.exit,
                handler = EssenceMineCrossing.ID, cost = 5, bidirectional = false,
                requirements = Requirements(flags = setOf(EssenceMineCrossing.exitFlag(wizard))))
        }
        report.count("essence portal crossings", EssenceMineWizard.entries.size)
    }

    /**
     * Adds the KBD levers as ordinary one-click entrances, using their existing teleport destinations.
     */
    private fun lever(obj: GameObject, lever: WildernessLever, climbs: MutableList<GeneratedObstacle>, report: GenerationReport) {
        // A lever on a wall is pulled from its own tile, so the bot must stand where the pull can reach.
        val from = (climbingTiles(obj) + obj.position).firstOrNull {
            world.collisionManager.reached(it, obj, InteractionPolicy.STANDARD_SIZE) &&
                    (it != obj.position || LadderDestination.canLand(it))
        }
        val option = obj.def().actions.getOrNull(0)
        if (from == null || option == null || !LadderDestination.canLand(lever.destination)) {
            report.skipped += "lever ${obj.id} at ${obj.position}: no interaction tile, option or landing"
            return
        }
        climbs += GeneratedObstacle(EdgeType.ENTRANCE, obj.id, obj.position, from, lever.destination,
            option, cost = lever.messageDelay + 6, bidirectional = false)
        report.count("KBD levers")
    }

    /**
     * Generates the actual pressure-door and support collider crossings; native handlers own their opening rules.
     */
    private fun waterbirth(obj: GameObject, climbs: MutableList<GeneratedObstacle>, report: GenerationReport) {
        val gate = Waterbirth.Gate.entries.firstOrNull { it.position == obj.position }
        val support = Waterbirth.support(obj.position)
        val from = gate?.west ?: support?.outside ?: return
        val to = gate?.east ?: support!!.inside
        if (!LadderDestination.canLand(from) || !LadderDestination.canLand(to)) {
            report.skipped += "Waterbirth barrier ${obj.id} at ${obj.position}: no stand or landing"
            return
        }
        climbs += GeneratedObstacle(EdgeType.CROSSING, obj.id, obj.position, from, to,
            handler = WaterbirthCrossing.ID, cost = 80, bidirectional = true)
        report.count("Waterbirth barriers")
    }

    /**
     * Jarvald lands directly on shore, so his two directions are NPC crossings rather than deck voyages.
     */
    private fun jarvald(climbs: MutableList<GeneratedObstacle>, report: GenerationReport) {
        for (route in Jarvald.Route.entries) {
            val from = standableNear(route.departure)
            if (from == null || !LadderDestination.canLand(route.destination) ||
                world.locator.findNpcs(route.departure, 8) { it.id in route.crew }.isEmpty()) {
                report.skipped += "Jarvald ${route.place}: missing crew, departure stand or landing"
                continue
            }
            climbs += GeneratedObstacle(EdgeType.CROSSING, null, route.departure, from, route.destination,
                handler = JarvaldCrossing.ID, cost = 12, requirements = Requirements(flags = setOf(JarvaldCrossing.PAYMENT)),
                bidirectional = false, npcId = route.crew.first())
            report.count("Jarvald crossings")
        }
    }

    /**
     * The paired outward leg covers two real corridors with a common central-room landing. Return travel uses
     * the individual native barriers and needs no partner. Existing generated webs are rebuilt on every startup.
     */
    private fun waterbirthParty(climbs: MutableList<GeneratedObstacle>, report: GenerationReport) {
        val from = WaterbirthPartyCrossing.FOYER
        val to = WaterbirthPartyCrossing.CENTER
        if (!LadderDestination.canLand(from) || !LadderDestination.canLand(to)) {
            report.skipped += "Waterbirth party: no foyer stand or central-room landing"
            return
        }
        climbs += GeneratedObstacle(EdgeType.CROSSING, Waterbirth.Gate.MIDDLE.id, Waterbirth.Gate.MIDDLE.position,
            from, to, handler = WaterbirthPartyCrossing.ID, cost = 130,
            requirements = Requirements(flags = setOf(WaterbirthPartyCrossing.READY)), bidirectional = false)
        report.count("Waterbirth paired crossings")
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
     * Spreads waypoints over the ground floor, both the overworld and the dungeons below it, so that no walk of the graph is
     * longer than the range of the route pathfinder. Cells that already have a waypoint, which [waypoints] are, are left alone.
     */
    private fun scatter(waypoints: List<GeneratedWaypoint>, report: GenerationReport): List<GeneratedWaypoint> {
        val table = ctx.cache.mapIndexTable
        val regions = table.allRegions.map { it.absPosition }
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
        val climbsText = WebWalkWriter.obstacles(gathered.climbs.filter { it.type != EdgeType.CROSSING })
        sources[WebWalkLoader.CLIMBS] = climbsText
        val crossings = WebWalkCrossingGenerator.generate(ctx.cache.mapIndexTable)
        crossings.report.counts.forEach { (what, amount) -> gathered.report.count(what, amount) }
        gathered.report.skipped += crossings.report.skipped
        val crossingsText = WebWalkWriter.obstacles(crossings.obstacles + gathered.climbs.filter { it.type == EdgeType.CROSSING })
        sources[WebWalkLoader.CROSSINGS] = crossingsText
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

        return Output(doors.obstacles, climbsText, crossingsText, teleportsText, banksText, walkGraphText, shipsText)
    }

    /**
     * The names of the generated files, which are all written by [write].
     */
    val GENERATED_FILES = listOf(WebWalkLoader.OBSTACLES, WebWalkLoader.CLIMBS, WebWalkLoader.CROSSINGS,
                                 WebWalkLoader.TELEPORTS, WebWalkLoader.BANKS, WebWalkLoader.WALK_GRAPH,
                                 WebWalkLoader.SHIPS)

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
     * mix of old and new files. The files depend on each other (the banks are linked to nodes of the walk graph, for one).
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
}

/**
 * Runs [WebWalkLiveGenerator]. It is run every time the server starts, before the server goes online.
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
     * Generates the files. Gathers on the game thread (so this must be called from it), then finishes on [executor]. It is
     * meant for the task pool of a [io.luna.game.event.impl.ServerStateChangedEvent], which the server waits for before it
     * goes online. If anything goes wrong it is logged and the files are left as they were, so a server that had them still
     * web-walks with the old ones, and one that didn't can't web-walk until the problem is fixed.
     *
     * @param executor Where the part that takes a while is run.
     */
    fun generate(executor: Executor) {
        val gathered = gather() ?: return
        executor.execute { finish(gathered) }
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
     * Links what was gathered and then writes the files.
     *
     * @param gathered What [gather] found.
     */
    private fun finish(gathered: WebWalkLiveGenerator.Gathered) {
        try {
            val output = WebWalkLiveGenerator.finish(gathered, DIRECTORY)
            gathered.report.summary().forEach { say(it) }
            WebWalkLiveGenerator.write(output, DIRECTORY)
            say("Wrote ${WebWalkLiveGenerator.GENERATED_FILES.joinToString()} to $DIRECTORY.")
        } catch (e: Throwable) {
            logger.error("Could not generate the web-walker data!", e)
        }
    }
}
