package api.bot.action.trading

/**
 * Bounds a marketplace visit by its total elapsed time and time without a useful completed exchange.
 * The total limit starts before travel. The idle limit starts when trading first becomes ready and survives
 * later banking, pauses and resumed initialization. Only useful settled transfers renew the idle allowance.
 * Supplied clock values are kept monotonic so a wall-clock correction cannot extend a visit.
 *
 * @property idleMillis Maximum time without useful trading progress after first readiness.
 * @property maximumMillis Maximum total elapsed time, including travel and preparation.
 * @author lare96
 */
internal class BotTradeVisitBudget(private val idleMillis: Long, private val maximumMillis: Long) {

    init {
        require(idleMillis > 0L)
        require(maximumMillis > 0L)
    }

    private var startedAt: Long? = null
    private var progressAt: Long? = null
    private var observedAt: Long? = null

    /**
     * Whether at least one useful exchange has actually settled during this visit.
     */
    var hadProgress = false
        private set

    /**
     * Starts the total visit limit once; repeated initialization retains the original start.
     */
    fun start(now: Long) {
        val current = observe(now)
        if (startedAt == null) startedAt = current
    }

    /**
     * Starts idle accounting on first readiness without renewing it after banking or a pause.
     */
    fun ready(now: Long) {
        val current = observe(now)
        start(current)
        if (progressAt == null) progressAt = current
    }

    /**
     * Renews idle time only after a useful native transfer; requests and rejected offers do not qualify.
     */
    fun settled(now: Long, useful: Boolean) {
        val current = observe(now)
        if (!useful) return
        ready(current)
        progressAt = current
        hadProgress = true
    }

    /**
     * Returns the remaining allowance, with the total visit limit taking precedence over idle renewals.
     */
    fun remaining(now: Long): Long {
        val current = observe(now)
        val idle = progressAt?.let { left(idleMillis, elapsed(current, it)) } ?: Long.MAX_VALUE
        return minOf(maximumRemaining(current), idle)
    }

    /**
     * Returns only the absolute allowance for suspending work whose idle allowance may renew after a sale.
     */
    fun maximumRemaining(now: Long): Long {
        val current = observe(now)
        return startedAt?.let { left(maximumMillis, elapsed(current, it)) } ?: maximumMillis
    }

    /**
     * Checks both limits at the supplied time.
     */
    fun expired(now: Long): Boolean = remaining(now) == 0L

    private fun observe(now: Long): Long = maxOf(observedAt ?: now, now).also { observedAt = it }

    private fun elapsed(now: Long, since: Long): Long {
        if (now <= since) return 0L
        return (now - since).takeIf { it >= 0L } ?: Long.MAX_VALUE
    }

    private fun left(allowance: Long, elapsed: Long): Long =
        if (elapsed >= allowance) 0L else allowance - elapsed
}
