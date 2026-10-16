package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import com.google.gson.JsonParser
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import java.nio.file.Files
import java.nio.file.Path

/**
 * Makes `obstacles.jsonc`, which holds every door, gate and curtain of the world, as it is when it is closed.
 *
 * The doors come from the ids in `data/game/world/doors/` that are found in the map data of the cache. This needs no
 * running world, so a test can run it against the cache to make sure that the committed file is still up to date. The
 * `generateWebWalk` Gradle task runs it with the rest of the generation, see [WebWalkLiveGenerator].
 *
 * Doors that the map places open are listed too, as the door that [game.obj.doors.Doors] closes them to, so that the web
 * knows about them when a player closes one. Only straight walls are listed, because those are the only ones that
 * [game.obj.doors.Doors] swings as gates and double doors. Diagonal doors, and doors that are not walls, are left out and
 * reported.
 *
 * The tiles on either side of a door are checked with the tile data of the cache alone, so a tile that is only blocked by
 * an object is still taken to be a tile that can be stood on. This is why doors are not checked against collision here.
 *
 * @author Hydrozoa
 */
object WebWalkDoorGenerator {

    /**
     * How a door swings, which is how [game.obj.doors.Doors] moves it between open and closed.
     */
    enum class Swing { SINGLE, DOUBLE, GATE, CURTAIN }

    /**
     * A door of the door files.
     *
     * @property closed The id of the door when it is closed.
     * @property open The id of the door when it is open.
     * @property type The kind of obstacle that it makes.
     * @property swing How it swings.
     * @property left If this is the left leaf of a double door or a gate, which is the hinge of a gate.
     */
    class DoorEntry(val closed: Int, val open: Int, val type: EdgeType, val swing: Swing, val left: Boolean)

    /**
     * The files of door ids, the kind of obstacle that their doors make and how they swing.
     */
    private val DOOR_FILES = listOf(Triple("doors.json", EdgeType.DOOR, Swing.SINGLE),
                                    Triple("double_doors.json", EdgeType.DOOR, Swing.DOUBLE),
                                    Triple("gates.json", EdgeType.GATE, Swing.GATE),
                                    Triple("curtains.json", EdgeType.CURTAIN, Swing.CURTAIN))

    /**
     * An open leaf of a door that the map placed open.
     */
    private class OpenLeaf(val door: DoorEntry, val position: Position, val direction: ObjectDirection)

    /**
     * The tile offset that [game.obj.doors.Doors] applies to a straight door when it closes.
     */
    fun closeOffset(direction: ObjectDirection): Pair<Int, Int> = when (direction) {
        ObjectDirection.WEST -> Pair(0, 1)
        ObjectDirection.NORTH -> Pair(1, 0)
        ObjectDirection.EAST -> Pair(0, -1)
        ObjectDirection.SOUTH -> Pair(-1, 0)
    }

    /**
     * Turns a direction a number of quarter turns, in the order west, north, east, south.
     */
    fun rotate(direction: ObjectDirection, quarterTurns: Int): ObjectDirection =
        ObjectDirection.ALL[(direction.id + quarterTurns) % 4]!!

    /**
     * The result of a generation.
     *
     * @property obstacles The text of `obstacles.jsonc`.
     * @property report What was made and what was left out.
     */
    class Result(val obstacles: String, val report: GenerationReport)

    /**
     * Reads the ids of the closed doors, keyed to the kind of obstacle that they make.
     *
     * @param directory The directory of the door files, which is `data/game/world/doors`.
     * @return The kind of obstacle for each id of a closed door.
     */
    fun readClosedIds(directory: Path): Map<Int, EdgeType> {
        val ids = LinkedHashMap<Int, EdgeType>()
        for (door in readDoors(directory)) {
            ids[door.closed] = door.type
        }
        return ids
    }

    /**
     * Reads the doors of the door files, in the order of the files.
     *
     * @param directory The directory of the door files, which is `data/game/world/doors`.
     * @return The doors.
     */
    fun readDoors(directory: Path): List<DoorEntry> {
        val entries = ArrayList<DoorEntry>()
        for ((file, type, swing) in DOOR_FILES) {
            val path = directory.resolve(file)
            if (!Files.exists(path)) {
                continue
            }
            val doors = Files.newBufferedReader(path).use { JsonParser.parseReader(it).asJsonArray }
            for (door in doors.map { it.asJsonObject }) {
                val left = door.get("side")?.asString.equals("LEFT", true)
                entries += DoorEntry(door.get("closed").asInt, door.get("open").asInt, type, swing, left)
            }
        }
        return entries
    }

    /**
     * Opens the cache and decodes its map data.
     *
     * @return The map data.
     */
    fun loadMap(): MapIndexTable {
        val cache = Cache()
        cache.open()
        cache.runDecoders(null, MapDecoder())
        cache.waitForDecoders()
        val table = cache.mapIndexTable
        cache.close()
        return table
    }

    /**
     * Makes the obstacles of every closed door in the map data.
     *
     * @param table The map data.
     * @param closedIds The kind of obstacle for the id of each closed door.
     * @param openDoors The doors to look for in their open state too, which are listed as the closed door that they close
     * to. An open id that is shared by several doors belongs to the first of them, like it does for [game.obj.doors.Doors].
     * @return The obstacles, which are not sorted, and what was left out.
     */
    fun generate(table: MapIndexTable, closedIds: Map<Int, EdgeType>, openDoors: List<DoorEntry> = emptyList()):
            Pair<List<GeneratedObstacle>, GenerationReport> {
        val report = GenerationReport()
        val map = MapTiles(table)
        val walls = LinkedHashMap<PlacedWall, Position>()
        for (placed in table.objectSet) {
            val type = closedIds[placed.objectId] ?: continue
            if (placed.type != ObjectType.STRAIGHT_WALL) {
                report.skipped += "${type.name} ${placed.objectId} at ${DoorObstacles.describe(placed.position)}: " +
                        "a ${placed.type} rather than a straight wall"
                continue
            }
            val wall = PlacedWall(placed.objectId, placed.position, placed.rotation, type)
            if (walls.putIfAbsent(wall, placed.position) != null) {
                report.skipped += "${type.name} ${placed.objectId} at ${DoorObstacles.describe(placed.position)}: " +
                        "listed twice in the map"
            }
        }
        closeOpenDoors(table, openDoors, walls, report)

        // The tiles that are stood on are one level lower where the wall is on a bridge, like the walls themselves.
        val obstacles = ArrayList<GeneratedObstacle>()
        for (wall in walls.keys) {
            val lowered = if (map.isBridged(wall.position)) 1 else 0
            val level = wall.position.z - lowered
            if (level < 0) {
                report.skipped += "${wall.type} ${wall.objectId} at ${DoorObstacles.describe(wall.position)}: " +
                        "below the bottom floor"
                continue
            }
            val from = Position(wall.position.x, wall.position.y, level)
            val other = DoorObstacles.otherSide(wall.position, wall.direction)
            val to = Position(other.x, other.y, level)
            if (!map.canStand(from) || !map.canStand(to)) {
                val tile = if (!map.canStand(from)) from else to
                report.skipped += "${wall.type} ${wall.objectId} at ${DoorObstacles.describe(wall.position)}: " +
                        "nothing to stand on at ${DoorObstacles.describe(tile)}"
                continue
            }
            obstacles += GeneratedObstacle(wall.type, wall.objectId, wall.position, from, to)
            report.count(wall.type.name.lowercase() + "s")
        }
        return Pair(obstacles, report)
    }

    /**
     * Adds the walls of the doors that the map placed open, as they are when [game.obj.doors.Doors] closes them. A door
     * that is closed again swings back to where its closed state stands, so that is where a player would find it.
     *
     * @param table The map data.
     * @param doors The doors to look for in their open state.
     * @param walls Where the closed walls are added to.
     * @param report Where the doors that are left out are reported.
     */
    private fun closeOpenDoors(table: MapIndexTable, doors: List<DoorEntry>, walls: MutableMap<PlacedWall, Position>,
                               report: GenerationReport) {
        val byOpenId = HashMap<Int, DoorEntry>()
        for (door in doors) {
            byOpenId.putIfAbsent(door.open, door)
        }
        val leaves = ArrayList<OpenLeaf>()
        for (placed in table.objectSet) {
            val door = byOpenId[placed.objectId] ?: continue
            if (placed.type != ObjectType.STRAIGHT_WALL) {
                report.skipped += "open ${door.type.name} ${placed.objectId} at ${DoorObstacles.describe(placed.position)}: " +
                        "a ${placed.type} rather than a straight wall"
                continue
            }
            leaves += OpenLeaf(door, placed.position, placed.rotation)
        }

        fun add(id: Int, type: EdgeType, position: Position, direction: ObjectDirection, from: OpenLeaf) {
            if (walls.putIfAbsent(PlacedWall(id, position, direction, type), from.position) != null) {
                report.skipped += "${type.name} $id at ${DoorObstacles.describe(position)}: listed twice in the map"
            }
        }

        // A leaf of a double door or a gate swings a quarter turn, to the side that its hinge is on.
        fun closedLeaf(leaf: OpenLeaf): Pair<Position, ObjectDirection> {
            val offset = closeOffset(leaf.direction).let { if (leaf.door.left) Pair(-it.first, -it.second) else it }
            return Pair(leaf.position.translate(offset.first, offset.second), rotate(leaf.direction, if (leaf.door.left) 1 else 3))
        }

        val rightGateLeaves = leaves.filter { it.door.swing == Swing.GATE && !it.door.left }.associateBy { it.position }
        for (leaf in leaves) {
            val door = leaf.door
            when (door.swing) {
                Swing.SINGLE -> {
                    val offset = closeOffset(leaf.direction)
                    add(door.closed, door.type, leaf.position.translate(offset.first, offset.second),
                        rotate(leaf.direction, 3), leaf)
                }
                Swing.CURTAIN -> add(door.closed, door.type, leaf.position, leaf.direction, leaf)
                Swing.DOUBLE -> {
                    val (position, direction) = closedLeaf(leaf)
                    add(door.closed, door.type, position, direction, leaf)
                }
                Swing.GATE -> {
                    // The hinge leaf finds the leaf that swings with it, and puts them back in line.
                    if (!door.left) {
                        continue
                    }
                    val toRight = closeOffset(leaf.direction)
                    val far = rightGateLeaves[leaf.position.translate(toRight.first, toRight.second)]
                    if (far == null) {
                        report.skipped += "open ${door.type.name} ${door.open} at ${DoorObstacles.describe(leaf.position)}: " +
                                "no other leaf next to it"
                        continue
                    }
                    val (position, direction) = closedLeaf(leaf)
                    val along = closeOffset(direction)
                    add(door.closed, door.type, position, direction, leaf)
                    add(far.door.closed, far.door.type, position.translate(along.first, along.second), direction, far)
                }
            }
        }
    }

    /**
     * Makes `obstacles.jsonc` from the files of the project.
     *
     * @param doorDirectory The directory of the door files.
     * @param table The map data.
     * @return The text of the file and what was left out.
     */
    fun generate(doorDirectory: Path, table: MapIndexTable): Result {
        val doors = readDoors(doorDirectory)
        val (obstacles, report) = generate(table, doors.associate { it.closed to it.type }, doors)
        return Result(WebWalkWriter.obstacles(obstacles), report)
    }

    /**
     * Looks at the tiles of the map data.
     */
    private class MapTiles(private val table: MapIndexTable) {

        /**
         * Determines if a tile of the map data, which has map coordinates, is on a bridge.
         */
        fun isBridged(position: Position): Boolean = tile(position.x, position.y, 1)?.isBridge == true

        /**
         * Determines if a tile, at the height that it is stood on, has a floor that is not blocked.
         */
        fun canStand(position: Position): Boolean {
            // A bridged column has its tiles one level up in the map data.
            val raw = if (isBridged(position)) position.z + 1 else position.z
            if (raw > 3) {
                return false
            }
            val tile = tile(position.x, position.y, raw) ?: return false
            return !tile.isBlocked && (tile.overlay != 0 || tile.underlay != 0)
        }

        /**
         * Gets a tile of the map data, or `null` if the map has none there.
         */
        private fun tile(x: Int, y: Int, z: Int) =
            Position(x, y, z).let { if (table.indexTable.containsKey(it.region)) table.getTile(it) else null }
    }
}
