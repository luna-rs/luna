package engine.bot.economy

import api.attr.Attr
import api.bot.script.ProductionPlan
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.bot.Bot

/**
 * Counts observed bot processing and item depletion without scanning players or retaining their identities.
 * All updates and inspection run on the game thread. Counters describe activity since this process started,
 * not world inventory or a complete record of every source of an item.
 *
 * @author lare96
 */
object BotProductionMetrics {

    /**
     * The existing decision owner, rather than a new telemetry stream or background collector.
     *
     * @author lare96
     */
    enum class Source { PLANNER, PRODUCTION, GATHERING }

    /**
     * Bounded outcome categories. Policy deferrals and successful completion remain distinct from failures.
     *
     * @author lare96
     */
    enum class Reason {
        SELECTED, COMPLETED, COOLDOWN, INVALID_REQUEST, ACCOUNT_RESTRICTION, LEVEL, NO_EXPERIENCE,
        NO_ELIGIBLE_RECIPE, NO_INPUT_PRICE, MATERIALS_UNAVAILABLE, TOOL_UNAVAILABLE, NO_SPACE,
        INSUFFICIENT_COINS, UNECONOMIC, EXPIRED, ACQUISITION_EXHAUSTED, ACTION_EXHAUSTED,
        BANK_EXHAUSTED, WITHDRAWAL_FAILED, NO_SOURCE, CROWDING, NO_LONGER_USEFUL,
        CONTINUATION_UNAVAILABLE, CANCELLED
    }

    /**
     * One account's latest immutable outcome, containing only bounded text and scalars already in scope.
     * Coin figures are the plan's recorded direct spending and ceiling; native market escrow is not queried.
     *
     * @author lare96
     */
    data class Decision(val source: Source, val reason: Reason, val recipeKey: String?, val skill: Int,
                        val itemId: Int, val completedOperations: Int, val remainingOperations: Int,
                        val stage: Int, val stages: Int, val maximumCoins: Long, val spentCoins: Long,
                        val deadline: Long, val observedAt: Long)

    /**
     * Fixed-size game-thread counters; no recipe keys, accounts or arbitrary strings become ledger keys.
     *
     * @author lare96
     */
    internal class DecisionCounts {
        private val values = LongArray(Source.entries.size * Reason.entries.size)

        /**
         * Saturates long-running totals without allocating a decision history.
         */
        fun record(source: Source, reason: Reason, amount: Long = 1L) {
            if (amount < 1L) return
            val index = source.ordinal * Reason.entries.size + reason.ordinal
            values[index] += minOf(amount, Long.MAX_VALUE - values[index])
        }

        fun count(source: Source, reason: Reason): Long =
            values[source.ordinal * Reason.entries.size + reason.ordinal]
    }

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
    private val decisions = DecisionCounts()
    private val latestDecision = Attr.nullableObj(Decision::class)
    private var merchantSales = MerchantSales(0, 0, 0, 0)

    /**
     * Native merchant goods whose coin payment settled, with their estimated input cost.
     *
     * @author lare96
     */
    data class MerchantSales(val units: Long, val netCoins: Long, val estimatedCost: Long, val totalFillMillis: Long)

    /**
     * Records the verified trade completion on the game thread.
     */
    fun recordMerchantSale(units: Int, coins: Long, estimatedCost: Long, delay: Long) {
        val previous = merchantSales
        merchantSales = MerchantSales(previous.units + units, previous.netCoins + coins,
            previous.estimatedCost + estimatedCost, previous.totalFillMillis + delay * units)
    }

    fun merchantSales(): MerchantSales = merchantSales

    /**
     * Records one existing decision boundary without reading inventories, prices, offers or the bot population.
     * Each account retains only its newest observation, and these diagnostics are never saved with the account.
     */
    fun recordDecision(bot: Bot, source: Source, reason: Reason, recipeKey: String? = null,
                       skill: Int = -1, itemId: Int = -1, plan: ProductionPlan? = null) {
        val native = plan?.recipe
        val key = (recipeKey ?: native?.key)?.take(128)?.replace('\n', ' ')?.replace('\r', ' ')
        bot.attributes()[latestDecision.attr] = Decision(source, reason, key, plan?.goalSkill ?: skill, itemId,
            plan?.completedOperations ?: 0, plan?.remainingOperations ?: 0,
            plan?.let { it.currentStageIndex + 1 } ?: 0, plan?.recipes?.size ?: 0,
            plan?.maximumCoins ?: 0L, plan?.spentCoins ?: 0L, plan?.deadline ?: 0L,
            System.currentTimeMillis())
        decisions.record(source, reason)
    }

    /**
     * Returns only the selected account's newest observation; no mutable game objects escape.
     */
    fun latestDecision(bot: Bot): Decision? = bot.attributes()[latestDecision.attr]

    /**
     * Reads one fixed enum counter describing observed boundaries since this process started.
     */
    fun decisionCount(source: Source, reason: Reason): Long = decisions.count(source, reason)

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
