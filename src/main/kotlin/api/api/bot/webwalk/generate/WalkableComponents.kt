package api.bot.webwalk.generate

import io.luna.game.model.Position

/**
 * Finds which positions can be walked between, by flood filling the tiles that can be reached from them.
 *
 * Two positions are in the same component if one can be walked to from the other. This is far cheaper than finding out
 * with a pathfinder, which has to search everything that can be reached before it gives up on a position that can't be.
 *
 * @param canStep Determines if a single step can be taken from a tile, with the x, y and plane of the tile and the x and
 * y direction of the step, which are each `-1`, `0` or `1`.
 *
 * @author Hydrozoa
 */
class WalkableComponents(private val canStep: (x: Int, y: Int, z: Int, dx: Int, dy: Int) -> Boolean) {

    companion object {

        /**
         * The steps that can be taken from a tile.
         */
        private val STEPS = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0, 1 to 1, 1 to -1, -1 to -1, -1 to 1)

        /**
         * Packs a tile into a single number.
         */
        private fun pack(x: Int, y: Int, z: Int) = (z.toLong() shl 40) or (x.toLong() shl 20) or y.toLong()
    }

    /**
     * Gives every position the number of the component that it is in.
     *
     * @param positions The positions.
     * @return The component of each position, in the same order. Positions that can be walked between share a number.
     */
    fun label(positions: List<Position>): IntArray {
        val components = IntArray(positions.size) { -1 }
        val startsByTile = HashMap<Long, MutableList<Int>>()
        for ((index, position) in positions.withIndex()) {
            startsByTile.getOrPut(pack(position.x, position.y, position.z)) { ArrayList() } += index
        }

        var next = 0
        for ((index, start) in positions.withIndex()) {
            if (components[index] != -1) {
                continue
            }
            // The tiles of two components never overlap, so what a fill visited is not needed once it is done.
            val component = next++
            val visited = HashSet<Long>()
            val queue = ArrayDeque<Long>()
            val first = pack(start.x, start.y, start.z)
            visited += first
            queue.addLast(first)
            while (queue.isNotEmpty()) {
                val tile = queue.removeFirst()
                val x = ((tile shr 20) and 0xFFFFF).toInt()
                val y = (tile and 0xFFFFF).toInt()
                val z = (tile shr 40).toInt()
                startsByTile[tile]?.forEach { components[it] = component }
                for ((dx, dy) in STEPS) {
                    val nextX = x + dx
                    val nextY = y + dy
                    if (nextX < 0 || nextY < 0) {
                        continue
                    }
                    val key = pack(nextX, nextY, z)
                    if (key !in visited && canStep(x, y, z, dx, dy)) {
                        visited += key
                        queue.addLast(key)
                    }
                }
            }
        }
        return components
    }
}
