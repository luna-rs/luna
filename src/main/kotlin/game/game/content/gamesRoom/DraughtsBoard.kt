package game.content.gamesRoom

import kotlin.math.abs

/**
 * Burthorpe draughts: compulsory captures, chained jumps and promotion on the back rank.
 * Positive pieces belong to player one; negative pieces belong to player two. Kings have magnitude two.
 *
 * @author lare96
 */
class DraughtsBoard(firstPlayer: Int = 1) {

    private val cells = IntArray(64) { slot ->
        when {
            (slot / 8 + slot % 8) % 2 == 0 -> 0
            slot / 8 < 3 -> -1
            slot / 8 > 4 -> 1
            else -> 0
        }
    }
    var turn = firstPlayer
        private set
    var winner = 0
        private set
    var forcedPiece = -1
        private set
    var finished = false
        private set
    private var quietMoves = 0

    init {
        require(firstPlayer in 1..2)
    }

    fun snapshot(): IntArray = cells.copyOf()

    private fun owner(piece: Int): Int = when {
        piece > 0 -> 1
        piece < 0 -> 2
        else -> 0
    }

    private fun candidates(from: Int, capture: Boolean): List<Pair<Int, Int>> {
        if (owner(cells[from]) != turn) return emptyList()
        val distance = if (capture) 2 else 1
        val directions = if (abs(cells[from]) == 2) listOf(-1, 1) else listOf(if (turn == 1) -1 else 1)
        return directions.flatMap { dy ->
            listOf(-1, 1).mapNotNull { dx ->
                val x = from % 8 + dx * distance
                val y = from / 8 + dy * distance
                if (x !in 0..7 || y !in 0..7) return@mapNotNull null
                val to = y * 8 + x
                if (cells[to] != 0) return@mapNotNull null
                if (capture && owner(cells[(from / 8 + dy) * 8 + from % 8 + dx]) != 3 - turn) {
                    return@mapNotNull null
                }
                from to to
            }
        }
    }

    fun legalMoves(): List<Pair<Int, Int>> {
        if (finished) return emptyList()
        val sources = if (forcedPiece >= 0) listOf(forcedPiece) else (0..63).toList()
        val captures = sources.flatMap { candidates(it, true) }
        return if (captures.isNotEmpty() || forcedPiece >= 0) captures else sources.flatMap { candidates(it, false) }
    }

    /**
     * Applies one legal move. A multiple capture retains the turn and restricts the next move to that piece.
     */
    fun move(player: Int, from: Int, to: Int): Boolean {
        if (player != turn || (from to to) !in legalMoves()) return false
        val piece = cells[from]
        val capture = abs(from / 8 - to / 8) == 2
        cells[from] = 0
        cells[to] = piece
        if (capture) cells[(from + to) / 2] = 0
        val promoted = abs(piece) == 1 && (to / 8 == 0 && player == 1 || to / 8 == 7 && player == 2)
        if (promoted) cells[to] = piece * 2
        quietMoves = if (capture || promoted) 0 else quietMoves + 1
        forcedPiece = -1
        if (capture && !promoted && candidates(to, true).isNotEmpty()) {
            forcedPiece = to
        } else {
            turn = 3 - turn
            if (legalMoves().isEmpty()) {
                winner = player
                finished = true
            } else if (quietMoves >= 40) {
                finished = true
            }
        }
        return true
    }
}
