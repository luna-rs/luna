package engine.bot.economy

import io.luna.game.model.def.ItemDefinition

/**
 * Counts observed bot processing and item depletion without scanning players or retaining their identities.
 * All updates and inspection run on the game thread. Counters describe activity since this process started,
 * not world inventory or a complete record of every source of an item.
 *
 * @author lare96
 */
object BotProductionMetrics {

    /**
     * Immutable per-item activity. Processing an input is separate from permanently depleting an item.
     *
     * @author lare96
     */
    data class ItemFlow(val processed: Long, val produced: Long, val depleted: Long,
                        val firstObserved: Long, val lastObserved: Long)

    /**
     * A bounded, game-thread ledger; reaching capacity never changes gameplay or evicts existing history.
     *
     * @author lare96
     */
    internal class Ledger(private val capacity: Int = 4_096) {
        private val items = HashMap<Int, ItemFlow>()

        /**
         * Adds nonnegative verified deltas with saturation rather than overflowing long-running totals.
         */
        fun record(id: Int, processed: Long = 0, produced: Long = 0, depleted: Long = 0,
                   now: Long = System.currentTimeMillis()) {
            if (id < 0 || id == 995 || processed < 0 || produced < 0 || depleted < 0 ||
                processed == 0L && produced == 0L && depleted == 0L) return
            val old = items[id]
            if (old == null && items.size >= capacity) return
            fun add(first: Long, second: Long) = first + minOf(second, Long.MAX_VALUE - first)
            items[id] = ItemFlow(add(old?.processed ?: 0, processed), add(old?.produced ?: 0, produced),
                add(old?.depleted ?: 0, depleted), old?.firstObserved ?: now, maxOf(old?.lastObserved ?: now, now))
        }

        /**
         * Returns an immutable snapshot in constant time without allocating an all-item report.
         */
        fun snapshot(id: Int): ItemFlow? = items[id]
    }

    private val ledger = Ledger()

    /**
     * Records actual input removal and output creation after a native production batch has settled.
     */
    fun recordConversion(inputId: Int, consumed: Long, outputId: Int?, produced: Long) {
        ledger.record(baseId(inputId), processed = consumed.coerceAtLeast(0))
        if (outputId != null) ledger.record(baseId(outputId), produced = produced.coerceAtLeast(0))
    }

    /**
     * Records an observed native use or alchemy removal; banking and trading never call this method.
     */
    fun recordSink(id: Int, amount: Long = 1) {
        ledger.record(baseId(id), depleted = amount.coerceAtLeast(0))
    }

    /**
     * Normalizes bank notes before reading a single item's counters.
     */
    fun snapshot(id: Int): ItemFlow? = ledger.snapshot(baseId(id))

    private fun baseId(id: Int): Int = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)
}
