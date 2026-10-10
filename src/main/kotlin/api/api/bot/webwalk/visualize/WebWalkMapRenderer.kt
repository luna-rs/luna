package api.bot.webwalk.visualize

import api.bot.webwalk.data.WebWalkLoader
import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.model.NodeKind
import api.bot.webwalk.model.WebWalkGraph
import io.luna.game.cache.Cache
import io.luna.game.cache.codec.MapDecoder
import io.luna.game.cache.codec.ObjectDefinitionDecoder
import io.luna.game.cache.map.MapIndexTable
import io.luna.game.model.Position
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Draws the web-walker graph over the terrain of the cache as a PNG, to get a quick idea of what the web looks like.
 *
 * The terrain is painted like the client does, see [WebWalkTerrain], with the walls over it. Nodes are
 * drawn as dots and edges as lines, with a colour for each [EdgeType] and [NodeKind], and a legend in the corner. Banks are
 * squares on the tile in front of their booth, with a line to the booth. An edge
 * that has only one end on the drawn plane, like a ladder, is drawn as a ring on that end. One-way edges get an arrowhead.
 *
 * The `renderWebWalkMap` Gradle task runs this, see its properties in `build.gradle.kts`.
 *
 * @author Hydrozoa
 */
object WebWalkMapRenderer {

    /**
     * The tiles that are drawn, from the south-west corner to the north-east corner, both included.
     *
     * @property minX The lowest x.
     * @property minY The lowest y.
     * @property maxX The highest x.
     * @property maxY The highest y.
     */
    data class Bounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int) {

        /**
         * The number of tiles from west to east.
         */
        val width: Int get() = maxX - minX + 1

        /**
         * The number of tiles from south to north.
         */
        val height: Int get() = maxY - minY + 1

        /**
         * @return `true` if the position is within the bounds, on any plane.
         */
        fun contains(position: Position) = position.x in minX..maxX && position.y in minY..maxY
    }

    /**
     * The tiles of the surface of the world, which is what is drawn unless told otherwise.
     */
    val OVERWORLD = Bounds(2048, 2496, 3903, 3967)

    /**
     * The colour of each type of edge.
     */
    val EDGE_COLORS = mapOf(EdgeType.WALK to Color(255, 235, 59),
                            EdgeType.DOOR to Color(255, 23, 68),
                            EdgeType.GATE to Color(255, 145, 0),
                            EdgeType.CURTAIN to Color(224, 64, 251),
                            EdgeType.LADDER to Color(0, 229, 255),
                            EdgeType.STAIR to Color(255, 255, 255),
                            EdgeType.TRAPDOOR to Color(124, 77, 255),
                            EdgeType.CROSSING to Color(255, 64, 129),
                            EdgeType.SHIP to Color(41, 121, 255),
                            EdgeType.TELEPORT to Color(118, 255, 3),
                            EdgeType.FAIRY_RING to Color(0, 230, 118))

    /**
     * The colour of each kind of node.
     */
    val NODE_COLORS = mapOf(NodeKind.WAYPOINT to Color(255, 255, 255),
                            NodeKind.OBSTACLE to Color(30, 30, 30),
                            NodeKind.TELEPORT_DEST to Color(118, 255, 3),
                            NodeKind.SHIP_PORT to Color(41, 121, 255),
                            NodeKind.FAIRY_RING to Color(0, 230, 118))

    /**
     * The width of an edge, in pixels, whatever the scale is, so that a map that is zoomed in on shows the same lines.
     */
    private const val LINE_WIDTH = 1f

    /**
     * The size of a node, in pixels, whatever the scale is. The arrowheads and the rings of edges that leave the plane follow it.
     */
    private const val DOT_SIZE = 4.8

    /**
     * The colour of banks.
     */
    private val BANK = Color(255, 196, 0)

    /**
     * The colour of walls.
     */
    private val WALL = Color.WHITE

    /**
     * The colour of trees, of dead trees and of the outline of both.
     */
    private val TREE = Color(46, 125, 50)
    private val DEAD_TREE = Color(121, 85, 72)
    private val TREE_OUTLINE = Color(15, 60, 20)

    /**
     * The names of objects that are trees, which are the common trees, the ones named for their wood, and palms.
     */
    private val TREE_NAME = Regex("\\b(tree|oak|willow|yew|palm)\\b")

    /**
     * The names that look like a tree but are not one that is standing.
     */
    private val NOT_TREE_NAME = Regex("stump|door|patch|branch|fallen|hollow")

    /**
     * The names of trees that are dead.
     */
    private val DEAD_TREE_NAME = Regex("dead|rotting")

    /**
     * Opens the cache and reads its map data and floor colours.
     *
     * @return The map data, and the floors.
     */
    fun loadTerrain(): Pair<MapIndexTable, List<WebWalkTerrain.Floor>> {
        val cache = Cache()
        cache.open()
        val floors = try {
            WebWalkTerrain.readFloors(cache)
        } catch (e: Exception) {
            emptyList()
        }
        cache.runDecoders(null, ObjectDefinitionDecoder(), MapDecoder())
        cache.waitForDecoders()
        val table = cache.mapIndexTable
        cache.close()
        return table to floors
    }

    /**
     * Draws the graph.
     *
     * @param table The map data.
     * @param floors The floors of the terrain.
     * @param graph The graph.
     * @param plane The plane to draw.
     * @param bounds The tiles to draw.
     * @param scale The number of pixels along the side of a tile.
     * @return The image, with north at the top.
     */
    fun render(table: MapIndexTable, floors: List<WebWalkTerrain.Floor>, graph: WebWalkGraph, plane: Int, bounds: Bounds, scale: Int):
            BufferedImage {
        val image = BufferedImage(bounds.width * scale, bounds.height * scale, BufferedImage.TYPE_INT_RGB)
        WebWalkTerrain.paint(image, table, floors, plane, bounds, scale)

        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val trees = drawTrees(g, table, plane, bounds, scale)
        val walls = drawWalls(g, table, plane, bounds, scale)
        val centre = scale / 2.0
        fun px(p: Position) = (p.x - bounds.minX) * scale + centre
        fun py(p: Position) = (bounds.maxY - p.y) * scale + centre
        fun visible(p: Position) = p.z == plane && bounds.contains(p)

        // TEMPORARY: the 16x16 cells of the scatter step, to see how it places its waypoints.
        g.color = Color(0, 0, 0, 150)
        g.stroke = BasicStroke(1f)
        for (x in Math.ceilDiv(bounds.minX, 16) * 16..bounds.maxX step 16) {
            val line = (x - bounds.minX) * scale
            g.drawLine(line, 0, line, image.height)
        }
        for (y in Math.ceilDiv(bounds.minY, 16) * 16..bounds.maxY step 16) {
            val line = (bounds.maxY - y + 1) * scale
            g.drawLine(0, line, image.width, line)
        }

        // An edge and its reverse are drawn once.
        val drawn = HashSet<Pair<String, String>>()
        val oneWay = graph.edges.map { it.from to it.to }.toHashSet()
        val counts = HashMap<EdgeType, Int>()
        val lineWidth = LINE_WIDTH
        val dot = DOT_SIZE

        // The walks go under the rest, so that the doors and ladders on them can be seen.
        for (edge in graph.edges.sortedBy { if (it.type == EdgeType.WALK) 0 else 1 }) {
            val a = graph.node(edge.from) ?: continue
            val b = graph.node(edge.to) ?: continue
            val aShown = visible(a.position)
            val bShown = visible(b.position)
            if (!aShown && !bShown) {
                continue
            }
            val key = if (edge.from < edge.to) edge.from to edge.to else edge.to to edge.from
            if (!drawn.add(key)) {
                continue
            }
            counts.merge(edge.type, 1, Int::plus)
            g.color = EDGE_COLORS.getValue(edge.type)
            if (aShown && bShown) {
                g.stroke = BasicStroke(lineWidth)
                g.drawLine(px(a.position).roundToInt(), py(a.position).roundToInt(),
                           px(b.position).roundToInt(), py(b.position).roundToInt())
                if ((edge.to to edge.from) !in oneWay) {
                    arrowhead(g, px(a.position), py(a.position), px(b.position), py(b.position), dot)
                }
            } else {
                val end = if (aShown) a.position else b.position
                g.stroke = BasicStroke(max(1f, lineWidth))
                val r = dot * 1.6
                g.drawOval((px(end) - r).roundToInt(), (py(end) - r).roundToInt(), (r * 2).roundToInt(), (r * 2).roundToInt())
            }
        }

        val kinds = HashMap<NodeKind, Int>()
        for (node in graph.nodes.values) {
            if (!visible(node.position)) {
                continue
            }
            kinds.merge(node.kind, 1, Int::plus)
            val x = px(node.position)
            val y = py(node.position)
            g.color = Color.BLACK
            g.fillOval((x - dot / 2 - 1).roundToInt(), (y - dot / 2 - 1).roundToInt(), (dot + 2).roundToInt(), (dot + 2).roundToInt())
            g.color = NODE_COLORS.getValue(node.kind)
            g.fillOval((x - dot / 2).roundToInt(), (y - dot / 2).roundToInt(), dot.roundToInt(), dot.roundToInt())
        }

        // Banks are not nodes, so they are drawn after them: a square on the tile in front of the booth, with a line to the booth.
        var banks = 0
        for (bank in graph.banks) {
            if (!visible(bank.stand)) {
                continue
            }
            banks++
            g.stroke = BasicStroke(LINE_WIDTH)
            g.color = BANK
            g.drawLine(px(bank.stand).roundToInt(), py(bank.stand).roundToInt(), px(bank.booth).roundToInt(), py(bank.booth).roundToInt())
            val side = dot.roundToInt() + 1
            val x = px(bank.stand).roundToInt() - side / 2
            val y = py(bank.stand).roundToInt() - side / 2
            g.color = Color.BLACK
            g.fillRect(x - 1, y - 1, side + 2, side + 2)
            g.color = BANK
            g.fillRect(x, y, side, side)
        }

        legend(g, plane, counts, kinds, banks, walls, trees)
        g.dispose()
        return image
    }

    /**
     * The plane that an object of the map data is drawn on. Objects on a bridge are one plane up in the map data, like its
     * floor.
     */
    private fun levelOf(table: MapIndexTable, pos: Position): Int {
        val bridged = pos.z < 3 && table.getTile(Position(pos.x, pos.y, 1)).isBridge
        return pos.z - (if (bridged) 1 else 0)
    }

    /**
     * Draws the trees of the map data as ellipses over the tiles that they stand on. Trees are the objects that are named
     * like one, which leaves out their stumps, patches and branches.
     *
     * @return The number of trees drawn.
     */
    private fun drawTrees(g: java.awt.Graphics2D, table: MapIndexTable, plane: Int, bounds: Bounds, scale: Int): Int {
        var drawn = 0
        val inset = max(1, scale / 8)
        for (obj in table.objectSet) {
            if (obj.type != ObjectType.DEFAULT && obj.type != ObjectType.DIAGONAL_DEFAULT) {
                continue
            }
            val pos = obj.position
            val definition = GameObjectDefinition.ALL.get(obj.objectId).orElse(null) ?: continue
            val name = definition.name?.lowercase() ?: continue
            if (!TREE_NAME.containsMatchIn(name) || NOT_TREE_NAME.containsMatchIn(name)) {
                continue
            }
            // The footprint is turned for objects that face north or south, like the collision of the object is.
            val turned = obj.rotation == ObjectDirection.NORTH || obj.rotation == ObjectDirection.SOUTH
            val sizeX = if (turned) definition.sizeY else definition.sizeX
            val sizeY = if (turned) definition.sizeX else definition.sizeY
            if (pos.x > bounds.maxX || pos.x + sizeX <= bounds.minX || pos.y > bounds.maxY || pos.y + sizeY <= bounds.minY ||
                !table.indexTable.containsKey(pos.region) || levelOf(table, pos) != plane) {
                continue
            }
            val x = (pos.x - bounds.minX) * scale + inset
            val y = (bounds.maxY - (pos.y + sizeY - 1)) * scale + inset
            val width = sizeX * scale - inset * 2
            val height = sizeY * scale - inset * 2
            g.color = if (DEAD_TREE_NAME.containsMatchIn(name)) DEAD_TREE else TREE
            g.fillOval(x, y, width, height)
            g.color = TREE_OUTLINE
            g.drawOval(x, y, width, height)
            drawn++
        }
        return drawn
    }

    /**
     * Draws the walls of the map data along the edges of the tiles that they stand on. A straight wall is one edge, a wall
     * corner is two, and diagonal walls are drawn across the tile. The pieces that only block one corner of a tile
     * (diagonal and rectangle corner walls) are left out, because they sit on joints that the other walls cover.
     *
     * @return The number of walls drawn.
     */
    private fun drawWalls(g: java.awt.Graphics2D, table: MapIndexTable, plane: Int, bounds: Bounds, scale: Int): Int {
        g.color = WALL
        g.stroke = BasicStroke(max(1f, scale / 4f), BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER)
        var drawn = 0
        for (obj in table.objectSet) {
            val type = obj.type
            if (type != ObjectType.STRAIGHT_WALL && type != ObjectType.WALL_CORNER && type != ObjectType.DIAGONAL_WALL) {
                continue
            }
            val pos = obj.position
            if (!bounds.contains(pos) || !table.indexTable.containsKey(pos.region)) {
                continue
            }
            if (levelOf(table, pos) != plane) {
                continue
            }
            val left = (pos.x - bounds.minX) * scale
            val top = (bounds.maxY - pos.y) * scale
            val right = left + scale
            val bottom = top + scale
            val rotation = when (obj.rotation) {
                ObjectDirection.WEST -> 0
                ObjectDirection.NORTH -> 1
                ObjectDirection.EAST -> 2
                else -> 3
            }
            fun edge(side: Int) = when (side % 4) {
                0 -> g.drawLine(left, top, left, bottom)
                1 -> g.drawLine(left, top, right, top)
                2 -> g.drawLine(right, top, right, bottom)
                else -> g.drawLine(left, bottom, right, bottom)
            }
            when (type) {
                ObjectType.STRAIGHT_WALL -> edge(rotation)
                ObjectType.WALL_CORNER -> {
                    edge(rotation)
                    edge(rotation + 1)
                }
                else ->
                    // Diagonal walls run corner to corner: west and east like a '/', north and south like a '\'.
                    if (rotation % 2 == 0) g.drawLine(left, bottom, right, top) else g.drawLine(left, top, right, bottom)
            }
            drawn++
        }
        return drawn
    }

    /**
     * Draws an arrowhead on the middle of the line from a to b.
     */
    private fun arrowhead(g: java.awt.Graphics2D, ax: Double, ay: Double, bx: Double, by: Double, size: Double) {
        val angle = atan2(by - ay, bx - ax)
        val mx = (ax + bx) / 2
        val my = (ay + by) / 2
        val spread = Math.PI / 6
        val x = IntArray(3)
        val y = IntArray(3)
        x[0] = (mx + cos(angle) * size).roundToInt()
        y[0] = (my + sin(angle) * size).roundToInt()
        x[1] = (mx - cos(angle - spread) * size).roundToInt()
        y[1] = (my - sin(angle - spread) * size).roundToInt()
        x[2] = (mx - cos(angle + spread) * size).roundToInt()
        y[2] = (my - sin(angle + spread) * size).roundToInt()
        g.fillPolygon(x, y, 3)
    }

    /**
     * Draws the legend, with what is drawn and how many of each, in the top-left corner.
     */
    private fun legend(g: java.awt.Graphics2D, plane: Int, edges: Map<EdgeType, Int>, nodes: Map<NodeKind, Int>,
                       banks: Int, walls: Int, trees: Int) {
        val rows = EdgeType.entries.filter { it in edges }.map { Triple(EDGE_COLORS.getValue(it), "${it.name} edges", edges.getValue(it)) } +
                NodeKind.entries.filter { it in nodes }.map { Triple(NODE_COLORS.getValue(it), "${it.name} nodes", nodes.getValue(it)) } +
                (if (banks > 0) listOf(Triple(BANK, "banks", banks)) else emptyList()) +
                Triple(WALL, "walls", walls) + Triple(TREE, "trees", trees)
        val font = Font(Font.SANS_SERIF, Font.BOLD, 18)
        g.font = font
        val line = 26
        val boxWidth = 260
        val boxHeight = line * (rows.size + 1) + 16
        g.color = Color(0, 0, 0, 200)
        g.fillRoundRect(12, 12, boxWidth, boxHeight, 12, 12)
        g.color = Color.WHITE
        g.drawString("Web walk, plane $plane", 24, 12 + 8 + 18)
        for ((index, row) in rows.withIndex()) {
            val y = 12 + 8 + line * (index + 1) + 18
            g.color = row.first
            g.fillRect(24, y - 12, 22, 12)
            g.color = Color.WHITE
            g.drawString("${row.second}: ${row.third}", 56, y)
        }
    }

    /**
     * Reads a system property, which the Gradle task sets, as a number.
     */
    private fun intProperty(name: String, default: Int): Int =
        System.getProperty(name)?.takeIf { it.isNotBlank() }?.toIntOrNull() ?: default

    /**
     * Runs the renderer. Properties are `luna.webwalk.map.plane`, `scale`, `bounds` (`minX,minY,maxX,maxY`, or `auto` to
     * fit the nodes of the plane), `dir` (of the web-walker files) and `out` (the PNG to write).
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val plane = intProperty("luna.webwalk.map.plane", 0)
        val scale = intProperty("luna.webwalk.map.scale", 3).coerceAtLeast(1)
        val directory = Path.of(System.getProperty("luna.webwalk.map.dir", "data/game/bots/webwalk"))
        val out = Path.of(System.getProperty("luna.webwalk.map.out", "build/webwalk/webwalk-map.png"))

        val graph = WebWalkLoader.load(directory)
        println("Loaded ${graph.nodes.size} nodes and ${graph.edges.size} edges from $directory.")

        val boundsText = System.getProperty("luna.webwalk.map.bounds")?.trim().orEmpty()
        val bounds = when {
            boundsText == "auto" -> {
                val onPlane = graph.nodes.values.map { it.position }.filter { it.z == plane }
                require(onPlane.isNotEmpty()) { "No nodes on plane $plane to fit the bounds to." }
                Bounds(onPlane.minOf { it.x } - 32, onPlane.minOf { it.y } - 32,
                       onPlane.maxOf { it.x } + 32, onPlane.maxOf { it.y } + 32)
            }
            boundsText.isNotEmpty() -> {
                val (a, b, c, d) = boundsText.split(',').map { it.trim().toInt() }
                Bounds(a, b, c, d)
            }
            else -> OVERWORLD
        }

        println("Decoding the cache...")
        val (table, floors) = loadTerrain()
        println("Drawing plane $plane, x ${bounds.minX}-${bounds.maxX}, y ${bounds.minY}-${bounds.maxY} at $scale px per tile...")
        val image = render(table, floors, graph, plane, bounds, scale)

        Files.createDirectories(out.toAbsolutePath().parent)
        ImageIO.write(image, "png", out.toFile())
        println("Wrote ${out.toAbsolutePath()} (${image.width}x${image.height}).")
    }
}
