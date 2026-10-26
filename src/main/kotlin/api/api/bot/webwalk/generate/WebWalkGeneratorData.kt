package api.bot.webwalk.generate

import api.bot.webwalk.model.BankLink
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.Requirements
import api.bot.webwalk.model.TeleportKind
import api.bot.webwalk.model.WebWalkNode
import io.luna.game.model.Position
import io.luna.game.model.mob.Skill
import io.luna.game.model.`object`.ObjectDirection
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A door, gate or curtain, in its closed state, placed in the world.
 *
 * @property objectId The id of the closed object.
 * @property position The position of the object.
 * @property direction The side of the tile that the wall is on.
 * @property type [EdgeType.DOOR], [EdgeType.GATE] or [EdgeType.CURTAIN].
 *
 * @author Hydrozoa
 */
data class PlacedWall(val objectId: Int, val position: Position, val direction: ObjectDirection, val type: EdgeType)

/**
 * An obstacle of `obstacles.jsonc` made by the generator.
 *
 * @property type The type of obstacle.
 * @property objectId The id of the object, in the state that it was found in.
 * @property pos The position of the object.
 * @property from The tile to cross it from.
 * @property to The tile that crossing it arrives at.
 * @property option The menu option to use on the object, or `null` to choose by the object's type.
 * @property handler The name of the crossing handler, for [EdgeType.CROSSING], and otherwise `null`.
 * @property cost The time to cross it in game ticks, or `null` to leave it to the planner.
 * @property requirements What a bot needs to cross it from [from] to [to].
 * @property reverseRequirements What a bot needs to cross it from [to] to [from], or `null` for the same as [requirements].
 * @property bidirectional If it can be crossed back, or `null` for the usual for its [type].
 *
 * @author Hydrozoa
 */
data class GeneratedObstacle(val type: EdgeType,
                             val objectId: Int,
                             val pos: Position,
                             val from: Position,
                             val to: Position,
                             val option: String? = null,
                             val handler: String? = null,
                             val cost: Int? = null,
                             val requirements: Requirements = Requirements.NONE,
                             val reverseRequirements: Requirements? = null,
                             val bidirectional: Boolean? = null)

/**
 * A waypoint of `walk_graph.jsonc` made by the generator.
 *
 * @property id The id of the waypoint.
 * @property pos The position of the waypoint.
 * @property tags The labels of the waypoint.
 *
 * @author Hydrozoa
 */
data class GeneratedWaypoint(val id: String, val pos: Position, val tags: Set<String> = emptySet())

/**
 * A walk edge of `walk_graph.jsonc` made by the generator, which is travelled both ways.
 *
 * @property from The id of one node.
 * @property to The id of the other node.
 * @property cost The cost of walking between them, in game ticks.
 *
 * @author Hydrozoa
 */
data class GeneratedLink(val from: String, val to: String, val cost: Int)

/**
 * A teleport of `teleports.jsonc` made by the generator.
 *
 * @property id The id of the teleport.
 * @property kind How the teleport is used.
 * @property key The name of the spell or jewellery, or `null` for the home teleport.
 * @property option The number of the destination in the dialogue of the jewellery, from 1, or `null` if it isn't jewellery.
 * @property dest Where the teleport arrives.
 * @property cost The time the teleport takes, in game ticks.
 * @property maxWilderness The deepest wilderness level it can be used from, or `null` for no limit.
 *
 * @author Hydrozoa
 */
data class GeneratedTeleport(val id: String,
                             val kind: TeleportKind,
                             val key: String?,
                             val option: Int?,
                             val dest: Position,
                             val cost: Int,
                             val maxWilderness: Int? = null)

/**
 * A ship of `ships.jsonc` made by the generator, from a [game.content.sailing.ShipRoute]. It is written as a route between
 * two ports of its own, [departureId] and [arrivalId].
 *
 * @property key The name of the route.
 * @property departure The tile on the dock where the crew is talked to.
 * @property arrival The tile on the dock below the gangplank that the ship is left by.
 * @property plankId The id of that gangplank.
 * @property plankPos The position of that gangplank.
 * @property cost The time from talking to the crew to stepping off the gangplank, in game ticks.
 * @property requirements What a bot needs to board.
 *
 * @author Hydrozoa
 */
data class GeneratedShip(val key: String,
                         val departure: Position,
                         val arrival: Position,
                         val plankId: Int,
                         val plankPos: Position,
                         val cost: Int,
                         val requirements: Requirements = Requirements.NONE) {

    /**
     * The id of the port that the ship is boarded at.
     */
    val departureId: String
        get() = "ship_${key.lowercase()}_departure"

    /**
     * The id of the port that the ship is left at.
     */
    val arrivalId: String
        get() = "ship_${key.lowercase()}_arrival"
}

/**
 * A bank booth that the generator found, before the tile to stand on in front of it has been chosen.
 *
 * @property id The id of the bank.
 * @property booth Where the booth is.
 * @property candidates The tiles next to the booth that can be stood on, which are on either side of its counter.
 *
 * @author Hydrozoa
 */
data class BankSeed(val id: String, val booth: Position, val candidates: List<Position>)

/**
 * A bank of `banks.jsonc` made by the generator.
 *
 * @property id The id of the bank.
 * @property booth Where the booth is.
 * @property stand The tile in front of the booth, that a bot walks to.
 * @property links The walks to [stand] from the nodes nearest to it, which [BankLinks] finds.
 *
 * @author Hydrozoa
 */
data class GeneratedBank(val id: String,
                         val booth: Position,
                         val stand: Position,
                         val links: List<BankLink> = emptyList())

/**
 * What the generator did, for people to read.
 *
 * @author Hydrozoa
 */
class GenerationReport {

    /**
     * Things that were left out, and why.
     */
    val skipped = ArrayList<String>()

    /**
     * How many of each kind of thing was made.
     */
    val counts = LinkedHashMap<String, Int>()

    /**
     * Counts one more of [what].
     */
    fun count(what: String, amount: Int = 1) {
        counts.merge(what, amount, Int::plus)
    }

    /**
     * @return A summary with the count of each thing, then the first [maxSkipped] things that were left out.
     */
    fun summary(maxSkipped: Int = 20): List<String> {
        val lines = ArrayList<String>()
        for ((what, amount) in counts) {
            lines += "$what: $amount"
        }
        lines += "skipped: ${skipped.size}"
        skipped.take(maxSkipped).forEach { lines += "  - $it" }
        if (skipped.size > maxSkipped) {
            lines += "  ... and ${skipped.size - maxSkipped} more"
        }
        return lines
    }
}

/**
 * Makes obstacles out of doors, gates and curtains.
 *
 * @author Hydrozoa
 */
object DoorObstacles {

    /**
     * The tile next to [position] that a wall facing [direction] stands between it and.
     */
    fun otherSide(position: Position, direction: ObjectDirection): Position = when (direction) {
        ObjectDirection.WEST -> position.translate(-1, 0)
        ObjectDirection.NORTH -> position.translate(0, 1)
        ObjectDirection.EAST -> position.translate(1, 0)
        ObjectDirection.SOUTH -> position.translate(0, -1)
    }

    /**
     * Makes an obstacle of every wall that has a tile that can be stood on at each side of it. Walls where it can't be
     * done are left out and reported.
     *
     * @param walls The closed doors, gates and curtains.
     * @param walkable Determines if a tile can be stood on.
     * @param report Where the left out walls are reported.
     * @return The obstacles, which are not sorted.
     */
    fun generate(walls: Iterable<PlacedWall>, walkable: (Position) -> Boolean, report: GenerationReport):
            List<GeneratedObstacle> {
        val obstacles = ArrayList<GeneratedObstacle>()
        for (wall in walls) {
            val from = wall.position
            val to = otherSide(from, wall.direction)
            if (!walkable(from) || !walkable(to)) {
                report.skipped += "${wall.type} ${wall.objectId} at ${describe(from)}: " +
                        "nothing to stand on at ${if (!walkable(from)) describe(from) else describe(to)}"
                continue
            }
            obstacles += GeneratedObstacle(wall.type, wall.objectId, wall.position, from, to)
            report.count(wall.type.name.lowercase() + "s")
        }
        return obstacles
    }

    /**
     * Describes a position for the report.
     */
    fun describe(position: Position) = "[${position.x}, ${position.y}, ${position.z}]"
}

/**
 * A place that should become a waypoint, before it has been moved onto a tile that can be stood on.
 *
 * @property id The id of the waypoint.
 * @property position Where the place is.
 * @property tags The labels of the waypoint.
 *
 * @author Hydrozoa
 */
data class WaypointSeed(val id: String, val position: Position, val tags: Set<String> = emptySet())

/**
 * Makes waypoints out of known places.
 *
 * @author Hydrozoa
 */
object WaypointSeeds {

    /**
     * Makes a waypoint of every seed, after moving it to a tile that can be stood on.
     *
     * Seeds that can't be moved to such a tile are left out and reported. A seed that lands on the tile of an earlier one
     * is left out too, but its tags are added to that waypoint.
     *
     * @param seeds The places, in the order that they win ties.
     * @param snap Finds the tile to use for a position, or `null` if there is none.
     * @param report Where the left out seeds are reported.
     * @return The waypoints, which are not sorted.
     */
    fun generate(seeds: Iterable<WaypointSeed>, snap: (Position) -> Position?, report: GenerationReport): List<GeneratedWaypoint> {
        val byPosition = LinkedHashMap<Position, GeneratedWaypoint>()
        val ids = HashSet<String>()
        for (seed in seeds) {
            val position = snap(seed.position)
            if (position == null) {
                report.skipped += "waypoint ${seed.id} at ${DoorObstacles.describe(seed.position)}: no tile to stand on nearby"
                continue
            }
            if (!ids.add(seed.id)) {
                report.skipped += "waypoint ${seed.id}: duplicate id"
                continue
            }
            val existing = byPosition[position]
            if (existing != null) {
                byPosition[position] = existing.copy(tags = existing.tags + seed.tags)
                report.skipped += "waypoint ${seed.id} at ${DoorObstacles.describe(position)}: same tile as ${existing.id}"
                continue
            }
            byPosition[position] = GeneratedWaypoint(seed.id, position, seed.tags)
            report.count("waypoints")
        }
        return byPosition.values.toList()
    }
}

/**
 * Chooses the tile to stand on in front of each bank booth.
 *
 * A booth is a counter with the customers on one side and the banker on the other, and the tiles on both sides can be stood
 * on. The banker's side is a small area that is walled off, so it is told apart by how much of the web is in the area that a
 * tile is in: the customers' side is part of the town, and the banker's side has nothing in it.
 *
 * @author Hydrozoa
 */
object BankStands {

    /**
     * Chooses the stand of every booth. Booths that have no tile with a node of the web in its area are left out and reported.
     *
     * @param seeds The booths, in the order that they win ties.
     * @param nodesIn Finds how many nodes of the web are in the area that a tile is in, which is where it can be walked to.
     * @param report Where the left out booths are reported, and booths that can be used from both sides are counted.
     * @return The banks, which are not sorted.
     */
    fun generate(seeds: Iterable<BankSeed>, nodesIn: (Position) -> Int, report: GenerationReport): List<GeneratedBank> {
        val banks = ArrayList<GeneratedBank>()
        for (seed in seeds) {
            val counts = seed.candidates.map { nodesIn(it) }
            val best = counts.maxOrNull() ?: 0
            if (best == 0) {
                report.skipped += "bank ${seed.id} at ${DoorObstacles.describe(seed.booth)}: no tile next to the booth is " +
                        "in an area with a node of the web"
                continue
            }
            if (counts.count { it == best } > 1) {
                // Nothing tells the sides apart, so the first one is used. Somebody should look at these.
                report.count("banks with both sides in the web")
            }
            banks += GeneratedBank(seed.id, seed.booth, seed.candidates[counts.indexOf(best)])
        }
        return banks
    }
}

/**
 * Links the stand of each bank to the nodes nearest to it, so that the planner doesn't have to find the nodes that a bank can
 * be walked from every time that it starts.
 *
 * The nodes are chosen like the planner chooses the nodes that a destination can be walked from: the nearest ones by distance,
 * of which the first few that can be walked from are kept.
 *
 * @author Hydrozoa
 */
object BankLinks {

    /**
     * Links every bank. Banks that no node can be walked from are left out and reported, because a trip could never arrive at
     * them.
     *
     * @param banks The banks, with their stands chosen.
     * @param nodes The nodes of the web that a bank may be linked to.
     * @param sameArea Determines if the position of a node can be walked to the stand of a bank at all, which is that they are in
     * the same walkable area. Nodes that it rejects are not tried, which saves the searches that would fail.
     * @param pathCost Gives the time, in game ticks, of walking between two positions on the same plane, or `null` if it can't
     * be walked.
     * @param radius How far, in tiles, nodes are looked for.
     * @param count How many nodes are linked to each bank, at most.
     * @param report Where the left out banks are reported, and the banks and their links are counted.
     * @return The banks with their links, which are not sorted.
     */
    fun generate(banks: Iterable<GeneratedBank>,
                 nodes: Collection<WebWalkNode>,
                 sameArea: (WebWalkNode, Position) -> Boolean,
                 pathCost: (Position, Position) -> Int?,
                 radius: Int,
                 count: Int,
                 report: GenerationReport): List<GeneratedBank> {
        val linked = ArrayList<GeneratedBank>()
        for (bank in banks) {
            val candidates = nodes.filter { it.position.z == bank.stand.z && it.position.computeLongestDistance(bank.stand) <= radius }
                .filter { sameArea(it, bank.stand) }
                .sortedWith(compareBy({ it.position.computeLongestDistance(bank.stand) }, { it.id }))

            val links = ArrayList<BankLink>()
            for (node in candidates) {
                if (links.size >= count) {
                    break
                }
                pathCost(node.position, bank.stand)?.let { links += BankLink(node.id, it) }
            }
            if (links.isEmpty()) {
                report.skipped += "bank ${bank.id} at ${DoorObstacles.describe(bank.booth)}: no node within $radius tiles of " +
                        "${DoorObstacles.describe(bank.stand)} can be walked from"
                continue
            }
            linked += bank.copy(links = links)
            report.count("banks")
            report.count("bank links", links.size)
        }
        return linked
    }
}

/**
 * Writes the generated files as text. The text is stable, with one entry on each line and entries in a fixed order, so
 * that generating again changes nothing unless the data changed. The only thing that changes every time is the [header],
 * which is not part of that text: it is put on the files when they are written.
 *
 * @author Hydrozoa
 */
object WebWalkWriter {

    /**
     * The start of the lines of a [header].
     */
    private const val COMMENT = "//"

    /**
     * Writes the header of a generated file: comments that say the file is generated and must not be edited by hand, and
     * when it was last rebuilt.
     *
     * @param generatedAt When the files were generated.
     * @return The header, ending with a blank line.
     */
    fun header(generatedAt: Instant): String =
        "$COMMENT This file is generated every time the server starts. Do not edit it by hand: the next start " +
                "overwrites any changes.\n" +
                "$COMMENT Last rebuilt: ${generatedAt.truncatedTo(ChronoUnit.SECONDS)}\n\n"

    /**
     * Writes `obstacles.jsonc`, `climbs.jsonc` or `crossings.jsonc`. What an obstacle doesn't set is left out.
     */
    fun obstacles(obstacles: Collection<GeneratedObstacle>): String {
        val sorted = obstacles.sortedWith(compareBy({ it.pos.z }, { it.pos.x }, { it.pos.y }, { it.objectId },
                                                    { it.type }, { it.from.z }, { it.from.x }, { it.from.y },
                                                    { it.to.z }, { it.to.x }, { it.to.y }))
        return lines(sorted.map { entry ->
            val handler = entry.handler?.let { ", \"handler\": ${quote(it)}" } ?: ""
            val option = entry.option?.let { ", \"option\": ${quote(it)}" } ?: ""
            val cost = entry.cost?.let { ", \"cost\": $it" } ?: ""
            val bidirectional = entry.bidirectional?.let { ", \"bidirectional\": $it" } ?: ""
            val requirements = if (entry.requirements.isEmpty) "" else ", \"requirements\": ${requirements(entry.requirements)}"
            val reverse = entry.reverseRequirements?.let { ", \"reverseRequirements\": ${requirements(it)}" } ?: ""
            "{ \"type\": \"${entry.type}\"$handler, \"object\": ${entry.objectId}, \"pos\": ${position(entry.pos)}, " +
                    "\"from\": ${position(entry.from)}, \"to\": ${position(entry.to)}$option$cost$bidirectional" +
                    "$requirements$reverse }"
        }, "[", "]")
    }

    /**
     * Writes requirements as an object that the loader reads, with the skills by lower case name. Nothing that isn't
     * required is written, so no requirements are `{}`.
     */
    fun requirements(requirements: Requirements): String {
        val fields = ArrayList<String>()
        if (requirements.skills.isNotEmpty()) {
            fields += "\"skills\": { " + requirements.skills.entries.sortedBy { it.key }
                .joinToString(", ") { "${quote(Skill.NAMES[it.key].lowercase())}: ${it.value}" } + " }"
        }
        if (requirements.items.isNotEmpty()) {
            fields += "\"items\": [" + requirements.items.joinToString(", ") { item ->
                "{ \"id\": ${item.id}" + (if (item.amount != 1) ", \"amount\": ${item.amount}" else "") + " }"
            } + "]"
        }
        if (requirements.coins != 0) {
            fields += "\"coins\": ${requirements.coins}"
        }
        if (requirements.flags.isNotEmpty()) {
            fields += "\"flags\": [" + requirements.flags.sorted().joinToString(", ") { quote(it) } + "]"
        }
        requirements.maxWilderness?.let { fields += "\"maxWilderness\": $it" }
        return if (fields.isEmpty()) "{}" else "{ ${fields.joinToString(", ")} }"
    }

    /**
     * Writes `teleports.jsonc`.
     */
    fun teleports(teleports: Collection<GeneratedTeleport>): String {
        return lines(teleports.sortedBy { it.id }.map { teleport ->
            val key = teleport.key?.let { ", \"key\": ${quote(it)}" } ?: ""
            val option = teleport.option?.let { ", \"option\": $it" } ?: ""
            val limits = ArrayList<String>()
            teleport.maxWilderness?.let { limits += "\"maxWilderness\": $it" }
            val requirements = if (limits.isEmpty()) "" else ", \"requirements\": { ${limits.joinToString(", ")} }"
            "{ \"id\": ${quote(teleport.id)}, \"kind\": \"${teleport.kind}\"$key$option, \"dest\": ${position(teleport.dest)}, " +
                    "\"cost\": ${teleport.cost}$requirements }"
        }, "[", "]")
    }

    /**
     * Writes `banks.jsonc`. The links of a bank are written nearest first, as [BankLinks] found them.
     */
    fun banks(banks: Collection<GeneratedBank>): String {
        return lines(banks.sortedBy { it.id }.map { bank ->
            val links = bank.links.joinToString(", ") { "{ \"node\": ${quote(it.nodeId)}, \"cost\": ${it.ticks} }" }
            "{ \"id\": ${quote(bank.id)}, \"booth\": ${position(bank.booth)}, \"pos\": ${position(bank.stand)}, " +
                    "\"links\": [$links] }"
        }, "[", "]")
    }

    /**
     * Writes `ships.jsonc`: the two ports of each ship, then the ship from one to the other.
     */
    fun ships(ships: Collection<GeneratedShip>): String {
        val sorted = ships.sortedBy { it.key }
        val portLines = sorted.flatMap { ship ->
            listOf("{ \"id\": ${quote(ship.departureId)}, \"pos\": ${position(ship.departure)} }",
                   "{ \"id\": ${quote(ship.arrivalId)}, \"pos\": ${position(ship.arrival)} }")
        }
        val routeLines = sorted.map { ship ->
            val requirements = if (ship.requirements.isEmpty) "" else ", \"requirements\": ${requirements(ship.requirements)}"
            "{ \"key\": ${quote(ship.key)}, \"from\": ${quote(ship.departureId)}, \"to\": ${quote(ship.arrivalId)}, " +
                    "\"object\": ${ship.plankId}, \"objectPos\": ${position(ship.plankPos)}, \"cost\": ${ship.cost}$requirements }"
        }
        return "{\n  \"ports\": ${lines(portLines, "[", "]", "  ")},\n  \"routes\": ${lines(routeLines, "[", "]", "  ")}\n}\n"
    }

    /**
     * Writes `walk_graph.jsonc`.
     */
    fun walkGraph(waypoints: Collection<GeneratedWaypoint>, links: Collection<GeneratedLink>): String {
        val waypointLines = waypoints.sortedBy { it.id }.map { waypoint ->
            val tags = if (waypoint.tags.isEmpty()) "" else
                ", \"tags\": [" + waypoint.tags.sorted().joinToString(", ") { quote(it) } + "]"
            "{ \"id\": ${quote(waypoint.id)}, \"pos\": ${position(waypoint.pos)}$tags }"
        }
        val linkLines = links.map { if (it.from <= it.to) it else GeneratedLink(it.to, it.from, it.cost) }
            .distinct().sortedWith(compareBy({ it.from }, { it.to })).map {
                "{ \"from\": ${quote(it.from)}, \"to\": ${quote(it.to)}, \"cost\": ${it.cost} }"
            }
        return "{\n  \"waypoints\": ${lines(waypointLines, "[", "]", "  ")},\n  \"edges\": ${lines(linkLines, "[", "]", "  ")}\n}\n"
    }

    /**
     * Writes a list with one entry on each line.
     */
    private fun lines(entries: List<String>, open: String, close: String, indent: String = ""): String {
        if (entries.isEmpty()) {
            return "$open$close" + if (indent.isEmpty()) "\n" else ""
        }
        val body = entries.joinToString(",\n") { "$indent  $it" }
        return "$open\n$body\n$indent$close" + if (indent.isEmpty()) "\n" else ""
    }

    /**
     * Writes a position as `[x, y, z]`.
     */
    private fun position(position: Position) = "[${position.x}, ${position.y}, ${position.z}]"

    /**
     * Writes a string with quotes, escaping what needs it.
     */
    private fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
