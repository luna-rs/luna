package game.content.barrows

/**
 * Raw model sequences and answer choices from Lost City's pinned Barrows puzzle script.
 * The first answer model is correct before shuffling. All four sequences are reachable.
 *
 * @author lare96
 */
enum class BarrowsPuzzle(val sequence: List<Int>, val answers: List<Int>) {
    ARROWS(listOf(6716, 6717, 6718), listOf(6713, 6714, 6715)),
    SQUARES(listOf(6722, 6723, 6724), listOf(6719, 6720, 6721)),
    BARS(listOf(6728, 6729, 6730), listOf(6725, 6726, 6727)),
    POLYGONS(listOf(6734, 6735, 6736), listOf(6731, 6732, 6733));

    companion object {
        val permutations = listOf(listOf(0, 1, 2), listOf(2, 0, 1), listOf(1, 2, 0),
                                  listOf(0, 2, 1), listOf(1, 0, 2), listOf(2, 1, 0))
    }
}
