package api.bot.action.trading

import io.luna.game.model.mob.Player
import java.time.Instant

/**
 * Stores and processes incoming trade requests for a bot.
 *
 * Requests are timestamped when received and remain valid for up to 10 seconds. When polled, expired or rejected
 * requests are removed until one request is accepted.
 *
 * @author lare96
 */
class BotTradeRequestHandler {

    /**
     * The pending trade requests and the time each request was received.
     */
    private val requests = HashMap<Player, Instant>()

    /**
     * Adds or refreshes a trade request from [plr].
     *
     * If the player already has a pending request, its timestamp is replaced with the current time.
     *
     * @param plr The player requesting a trade.
     */
    fun addRequest(plr: Player) {
        requests[plr] = Instant.now()
    }

    /**
     * Processes pending trade requests until one is accepted.
     *
     * Requests older than 10 seconds are ignored and removed. Valid requests are passed to [action]. If [action]
     * returns `false`, that request is removed and polling continues.
     *
     * If [action] returns `true`, polling stops immediately and the accepted player is returned. The accepted request
     * is removed, ancd any requests that have not yet been inspected remain stored for the next poll.
     *
     * @param action Determines whether a valid trade request should be accepted.
     * @return The first player whose request is accepted by [action], or `null` if no request is accepted.
     */
    fun pollRequests(action: (Player) -> Boolean): Player? {
        val it = requests.entries.iterator()
        while (it.hasNext()) {
            val entry = it.next()
            it.remove()
            if (entry.value.isAfter(Instant.now().minusSeconds(10)) && action(entry.key)) {
                return entry.key
            }
        }
        return null
    }
}