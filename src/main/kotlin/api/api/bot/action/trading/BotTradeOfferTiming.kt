package api.bot.action.trading

/**
 * Gives a player time to begin offering and finish each edit before the bot evaluates the basket.
 *
 * Callers supply elapsed monotonic milliseconds and the player's offer revision from the game thread.
 * Bot-side edits do not restart this quiet period.
 *
 * @author lare96
 */
class BotTradeOfferTiming(private val quietMillis: Long = 2_000L) {

    private var previousRevision: Long? = null
    private var changedAt = 0L

    init {
        require(quietMillis >= 0)
    }

    /**
     * Returns whether the initial wait or quiet period after the latest observed player edit has elapsed.
     */
    fun ready(revision: Long, nowMillis: Long): Boolean {
        if (previousRevision != revision) {
            previousRevision = revision
            changedAt = nowMillis
        }
        return nowMillis - changedAt >= quietMillis
    }
}
