package game.content.gamesRoom

/**
 * The seven-column, six-row RuneLink board used by the Burthorpe Games Room.
 * Cells are stored in the client's row-major order, with row zero at the top.
 *
 * @author lare96
 */
class RuneLinkBoard(firstPlayer: Int = 1) {

    private val cells = IntArray(42)
    var turn = firstPlayer
        private set
    var winner = 0
        private set
    var finished = false
        private set

    init {
        require(firstPlayer in 1..2)
    }

    fun snapshot(): IntArray = cells.copyOf()

    fun legalColumns(): List<Int> = if (finished) emptyList() else (0..6).filter { cells[it] == 0 }

    /**
     * Drops a piece, rejecting stale turns, invalid columns and moves after completion.
     */
    fun move(player: Int, column: Int): Boolean {
        if (finished || player != turn || column !in 0..6 || cells[column] != 0) return false
        val row = (5 downTo 0).first { cells[it * 7 + column] == 0 }
        cells[row * 7 + column] = player
        for ((dx, dy) in listOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)) {
            var length = 1
            for (sign in listOf(-1, 1)) {
                var x = column + dx * sign
                var y = row + dy * sign
                while (x in 0..6 && y in 0..5 && cells[y * 7 + x] == player) {
                    length++
                    x += dx * sign
                    y += dy * sign
                }
            }
            if (length >= 4) winner = player
        }
        finished = winner != 0 || cells.none { it == 0 }
        if (!finished) turn = 3 - turn
        return true
    }
}
