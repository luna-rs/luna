package api.bot.action.trading

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import java.util.concurrent.TimeUnit

/**
 * Explains one trade attempt to its human participant without repeating unchanged intentions.
 *
 * Callers supply short, already formatted text and the player's native offer revision on the game thread.
 * Successful notices share a three-second cooldown, and each distinct message is sent at most once per revision.
 * The shared notice sender checks current sessions, Trade/compete privacy, ignores and the complete packet size.
 *
 * @author lare96
 */
class BotTradeFeedback(private val bot: Bot, private val other: Player) {

    private val sent = HashSet<String>()
    private var sentRevision: Long? = null
    private var lastSentAt: Long? = null
    private val cooldownNanos = TimeUnit.SECONDS.toNanos(3)

    /**
     * Sends a complete blue trade notice when its revision and cooldown permit it.
     * Failed or oversized deliveries do not consume the cooldown or mark the message as sent.
     *
     * @return Whether the notice was queued to the current human participant.
     */
    fun send(text: String, revision: Long): Boolean {
        if (text.isBlank() || sentRevision == revision && text in sent) return false
        val now = System.nanoTime()
        if (lastSentAt?.let { now - it < cooldownNanos } == true) return false
        if (!PlayerTradeAdvertisementMessages.notice(bot, other, "@blu@${bot.username}: $text@bla@")) return false
        if (sentRevision != revision) {
            sent.clear()
            sentRevision = revision
        }
        sent += text
        lastSentAt = now
        return true
    }
}
