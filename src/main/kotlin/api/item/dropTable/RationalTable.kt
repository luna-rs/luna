package api.item.dropTable

import api.predef.*
import io.luna.util.Rational

/**
 * A rational drop simulation table.
 *
 * This class enables probabilistic selection based on [Rational] odds by constructing a virtual "slot-based" table
 * where each slot represents a proportion of the total odds.
 *
 * It supports sparse tables by allowing implied empty slots for unselected outcomes.
 *
 * @param nodes A list of (chance, element) pairs.
 *
 * @author lare96
 */
class RationalTable<T>(private val entries: List<Pair<Rational, T>>) {

    /**
     * Randomly selects a node using a virtual slot-based implementation. Empty slots are implicit when total chance
     * is < 1.0 (i.e., less than Rational.ALWAYS).
     *
     * @return A randomly chosen node or null if no node is selected.
     */
    fun roll(): T? {
        // Compute the total denominator to size the virtual table.
        val totalDenominator = entries.sumOf { it.first.denominator }
        if (totalDenominator == 0L) return null

        // Represents an entry's occupied range in the virtual table.
        data class SlotRange<T>(val range: LongRange, val value: T)

        // Build slot ranges based on scaled chance weights.
        var currentStart = 0L
        val ranges = entries.map { (chance, value) ->
            val slotsPerUnit = totalDenominator / chance.denominator
            val range = currentStart until (currentStart + chance.numerator * slotsPerUnit)
            currentStart = range.last + 1
            SlotRange(range, value)
        }

        // Roll a slot index and find which range it belongs to.
        val roll = rand().nextLong(0, totalDenominator)
        return ranges.firstOrNull { roll in it.range }?.value
    }
}