package game.content.fightCave

import io.luna.game.model.Position

/**
 * The Fight Cave's 63 waves and where their monsters appear.
 *
 * @author TheLining
 */
object FightCaveWaves {

    /**
     * The last wave, TzTok-Jad's.
     */
    const val LAST = 63

    /**
     * The monster ids by level (22, 45, 90, 180, 360, 702), each as its first and second id. A wave with two monsters
     * of one level uses both.
     */
    private val IDS = listOf(
        intArrayOf(2734, 2735), // Tz-Kih
        intArrayOf(2736, 2737), // Tz-Kek
        intArrayOf(2739, 2740), // Tok-Xil
        intArrayOf(2741, 2742), // Yt-MejKot
        intArrayOf(2743, 2744), // Ket-Zek
        intArrayOf(2745) // TzTok-Jad
    )

    /**
     * Each wave's monsters, highest level first. A wave keeps the last one's monsters, then either merges the
     * lowest pair of one level into one monster of the next level, or adds a Tz-Kih if there's no pair.
     */
    val MONSTERS: List<List<Int>> = run {
        val counts = IntArray(IDS.size)
        List(LAST) {
            val pair = counts.indexOfFirst { it == 2 }
            if (pair == -1) {
                counts[0]++
            } else {
                counts[pair] = 0
                counts[pair + 1]++
            }
            IDS.indices.reversed().flatMap { level -> List(counts[level]) { IDS[level][it] } }
        }
    }

    /**
     * The five places monsters appear (north-west, centre, south-east, south, south-west), as tiles of the real cave.
     * Each is the middle of the monster that appears there.
     */
    val SPAWN_POINTS = listOf(
        Position(2378, 5106),
        Position(2398, 5086),
        Position(2418, 5081),
        Position(2403, 5071),
        Position(2378, 5071)
    )

    /**
     * The south-west tile of a monster of [size] centred on [point].
     */
    fun spawnTile(point: Position, size: Int): Position = point.translate(-(size - 1) / 2, -(size - 1) / 2)
}
