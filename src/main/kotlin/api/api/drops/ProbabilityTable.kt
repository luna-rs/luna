package api.drops

import api.predef.*

/**
 * A table that selects one outcome from a list of probability entries.
 *
 * Each entry consists of a probability value and its associated result.
 *
 * When the total chance is less than or equal to `1.0`, the table behaves as an absolute probability table.
 * Any unclaimed probability space results in no selection being made.
 *
 * When the total chance exceeds `1.0`, the table falls back to relative-weight selection. In that mode, entry
 * values are treated as weights rather than absolute probabilities.
 *
 * @param entries The probability entries to roll against.
 *
 * @author lare96
 */
class ProbabilityTable<T>(private val entries: List<Pair<Double, T>>) {

    /**
     * Rolls this table and returns the selected value, if any.
     *
     * If the combined chance of all entries is less than or equal to `1.0`, this performs an absolute
     * probability roll over the range `[0.0, 1.0]`. If the roll lands outside all entry ranges, this returns
     * `null`.
     *
     * If the combined chance exceeds `1.0`, this performs a relative-weight roll over the range
     * `[0.0, totalChance]` instead. This preserves selection behavior for oversized tables, but the entry values
     * are no longer interpreted as absolute probabilities.
     *
     * @return The selected value, or `null` if no value is selected.
     */
    fun roll(): T? {
        if (entries.isEmpty()) {
            // No table to roll on.
            return null
        }

        val totalChance = entries.sumOf { it.first }
        val roll = if (totalChance > 1.0) rand().nextDouble(totalChance) else rand().nextDouble()
        var current = 0.0

        for ((chance, value) in entries) {
            // Advance the cumulative boundary until the rolled point is reached.
            current += chance
            if (roll < current) {
                return value
            }
        }

        // The roll landed in unclaimed probability space.
        return null
    }
}