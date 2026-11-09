package api.bot.action.trading

import api.bot.isRealBot
import io.luna.game.model.mob.Player
import java.time.Instant

/**
 * Stores and processes incoming trade requests for a bot.
 *
 * Requests are timestamped when received and remain valid for up to 10 seconds. When polled, expired or rejected
 * requests are removed until one request is accepted. Human requests take priority over bot requests.
 *
 * @author lare96
 */
class BotTradeRequestHandler(private val clock: () -> Instant = { Instant.now() }) {

    /**
     * The pending trade requests and the time each request was received.
     */
    private val requests = LinkedHashMap<Player, Instant>()

    /**
     * Adds or refreshes a trade request from [plr].
     *
     * If the player already has a pending request, its timestamp is replaced with the current time.
     *
     * @param plr The player requesting a trade.
     */
    fun addRequest(plr: Player) {
        // Account equality can span reconnects; retain the player instance that sent this request.
        requests.remove(plr)
        requests[plr] = clock()
    }

    /**
     * Tests live requests without consuming them, allowing an unanswered bot approach to yield to a human.
     * Expired requests are discarded even when no matching request is found.
     *
     * @param action Determines whether a pending request should take priority.
     * @return Whether any live pending request satisfies [action].
     */
    fun hasRequest(action: (Player) -> Boolean): Boolean {
        discardExpired()
        return requests.keys.any(action)
    }

    /**
     * Processes human requests before bot requests, oldest first within each group, until one is accepted.
     *
     * Requests older than 10 seconds are ignored and removed. Valid requests are passed to [action]. If [action]
     * returns `false`, that request is removed and polling continues.
     *
     * If [action] returns `true`, polling stops immediately and the accepted player is returned. The accepted request
     * is removed, and any requests that have not yet been inspected remain stored for the next poll.
     *
     * @param action Determines whether a valid trade request should be accepted.
     * @return The first player whose request is accepted by [action], or `null` if no request is accepted.
     */
    fun pollRequests(action: (Player) -> Boolean): Player? {
        return pollRequests({ true }, action)
    }

    /**
     * Processes human requests without inspecting or consuming pending autonomous-bot requests.
     * Connected players with automation facilities retain their human priority.
     */
    fun pollHumanRequests(action: (Player) -> Boolean): Player? = pollRequests({ !it.isRealBot() }, action)

    /**
     * Processes autonomous-bot requests while preserving human requests for the caller's higher-priority pass.
     */
    fun pollBotRequests(action: (Player) -> Boolean): Player? = pollRequests({ it.isRealBot() }, action)

    /**
     * Removes expired requests globally, then consumes only the selected group in priority and arrival order.
     */
    private fun pollRequests(include: (Player) -> Boolean, action: (Player) -> Boolean): Player? {
        discardExpired()
        val pending = requests.entries.filter { include(it.key) }
            .sortedWith(compareBy<Map.Entry<Player, Instant>> { it.key.isRealBot() }
            .thenBy { it.value })
        for (entry in pending) {
            requests.remove(entry.key)
            if (action(entry.key)) return entry.key
        }
        return null
    }

    /**
     * Removes requests that have reached the pending-request lifetime.
     */
    internal fun discardExpired() {
        if (requests.isEmpty()) return
        val oldest = clock().minusSeconds(10)
        requests.entries.removeIf { !it.value.isAfter(oldest) }
    }

    /**
     * Releases requester sessions when the receiving bot leaves the world.
     */
    internal fun clear() {
        requests.clear()
    }
}
