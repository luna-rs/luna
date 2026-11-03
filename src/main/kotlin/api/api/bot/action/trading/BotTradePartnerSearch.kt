package api.bot.action.trading

import kotlin.random.Random

/**
 * Spreads partner discovery over game ticks without retaining calculated offers or reserving partners.
 * Each sweep visits a random permutation once, retaining its position when the calculation budget is exhausted.
 * Callers must check current eligibility and calculate current offers on the game thread.
 *
 * @param initialTick The first tick on which this search can be used.
 * @param random The source of initial staggering and fair candidate ordering.
 * @author lare96
 */
internal class BotTradePartnerSearch<T>(initialTick: Long, private val random: Random = Random.Default) {

    companion object {

        /**
         * Fourteen normal game ticks preserve approximately the previous eight-second search cadence.
         */
        private const val SEARCH_INTERVAL_TICKS = 14

        /**
         * Bounds expensive failed matches for one bot on a single game tick.
         */
        private const val MAX_EVALUATIONS_PER_TICK = 8
    }

    private var nextSearchTick = initialTick + random.nextInt(SEARCH_INTERVAL_TICKS)
    private var lastEvaluationTick = Long.MIN_VALUE
    private var pending = emptyList<T>()
    private var cursor = 0

    /**
     * Returns the first current match, resuming an unfinished sweep before collecting new candidates.
     * Cheap eligibility is checked again when a deferred candidate is reached. No candidate is reconsidered in
     * that sweep, and repeated calls within the same tick cannot consume another calculation budget.
     */
    fun find(tick: Long, candidates: () -> List<T>, eligible: (T) -> Boolean, matches: (T) -> Boolean): T? {
        if (lastEvaluationTick == tick) return null
        if (pending.isEmpty()) {
            if (tick < nextSearchTick) return null
            nextSearchTick = tick + SEARCH_INTERVAL_TICKS
            pending = candidates().shuffled(random)
            cursor = 0
        }
        lastEvaluationTick = tick
        var evaluated = 0
        while (cursor < pending.size && evaluated < MAX_EVALUATIONS_PER_TICK) {
            val candidate = pending[cursor++]
            if (!eligible(candidate)) continue
            evaluated++
            if (matches(candidate)) {
                pending = emptyList()
                return candidate
            }
        }
        if (cursor == pending.size) pending = emptyList()
        return null
    }
}
