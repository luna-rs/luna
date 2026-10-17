package api.bot.webwalk.generate

import io.luna.game.model.Position
import java.util.Random

/**
 * Spreads waypoints over an area, so that no tile of it is far from a node of the web-walker graph.
 *
 * The area is divided into square cells, and each cell gets one waypoint at a random tile of its own, if it has a tile that can
 * be stood on. The tile is looked for around the centre of the cell, and farther from it with every tile that can't be stood
 * on, up to the edges of the cell. Two waypoints of neighbouring cells are never farther apart than twice the size of a cell, so the size of a cell
 * decides how far the walks of the graph are, which the pathfinder that does them has a range for.
 *
 * The tiles are random, but they are the same every time for the same seed: each cell draws from a generator of its own,
 * which only depends on the seed and the position of the cell, so the order the cells are visited in doesn't matter.
 *
 * @author Hydrozoa
 */
object ScatterWaypoints {

    /**
     * The tag of every waypoint made here.
     */
    const val TAG = "scatter"

    /**
     * How many random tiles are tried in a cell before it is given up on. The first are within a quarter of the cell's size from its
     * centre, and the last are anywhere in the cell.
     */
    private const val TRIES = 12

    /**
     * Makes the waypoints of an area.
     *
     * @param seed The seed of the random tiles.
     * @param cellSize The width and length of a cell, in tiles. Waypoints of cells next to each other are at most
     * `2 * cellSize - 1` tiles apart.
     * @param minX The smallest x of the area.
     * @param minY The smallest y of the area.
     * @param maxX The largest x of the area.
     * @param maxY The largest y of the area.
     * @param plane The plane of the waypoints.
     * @param occupied The cells that already have a node and are left empty, written as `x / cellSize` and `y / cellSize`.
     * @param standable Determines if a tile can be stood on, including that it is in the area that is wanted.
     * @return The waypoints, which are not sorted. The id of a waypoint is `scatter_<cell x>_<cell y>`.
     */
    fun generate(seed: Long, cellSize: Int, minX: Int, minY: Int, maxX: Int, maxY: Int, plane: Int,
                 occupied: Set<Pair<Int, Int>>, standable: (Position) -> Boolean): List<GeneratedWaypoint> {
        require(cellSize > 0) { "The size of a cell must be positive." }
        val waypoints = ArrayList<GeneratedWaypoint>()
        for (cellX in minX / cellSize..maxX / cellSize) {
            for (cellY in minY / cellSize..maxY / cellSize) {
                if (Pair(cellX, cellY) in occupied) {
                    continue
                }
                val random = Random((seed * 1_000_003L + cellX * 73_856_093L) xor (cellY * 19_349_663L))
                val centreX = cellX * cellSize + cellSize / 2
                val centreY = cellY * cellSize + cellSize / 2
                for (attempt in 0 until TRIES) {
                    // The tiles that are tried start close to the centre, and spread out towards the edges of the cell.
                    val spread = cellSize / 4 + cellSize / 4 * attempt / (TRIES - 1)
                    val x = (centreX + random.nextInt(spread * 2 + 1) - spread).coerceIn(cellX * cellSize, (cellX + 1) * cellSize - 1)
                    val y = (centreY + random.nextInt(spread * 2 + 1) - spread).coerceIn(cellY * cellSize, (cellY + 1) * cellSize - 1)
                    if (x < minX || x > maxX || y < minY || y > maxY) {
                        continue
                    }
                    val position = Position(x, y, plane)
                    if (standable(position)) {
                        waypoints += GeneratedWaypoint("${TAG}_${cellX}_$cellY", position, setOf(TAG))
                        break
                    }
                }
            }
        }
        return waypoints
    }
}
