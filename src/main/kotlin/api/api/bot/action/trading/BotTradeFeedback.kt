package api.bot.action.trading

import api.bot.Suspendable.waitFor
import api.bot.isRealBot
import api.predef.ext.*
import com.google.common.base.Stopwatch
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

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

    /**
     * Gives the player thirty seconds after a specific warning to respond on this exact trade screen.
     * Uses a server trade-status message if the directed notice could not be delivered.
     */
    suspend fun awaitResponse(offer: OfferTradeInterface, text: String, responded: () -> Boolean) {
        if (other.isRealBot() || offer.other !== other) return
        val confirmation = bot.overlays[ConfirmTradeInterface::class]?.takeIf { it.offer === offer }
        fun open(): Boolean = if (confirmation == null) bot.overlays[OfferTradeInterface::class] === offer
            else bot.overlays[ConfirmTradeInterface::class] === confirmation
        if (!open() || responded()) return

        val warning = "$text I'll wait 30 seconds."
        if (!PlayerTradeAdvertisementMessages.notice(bot, other, "@blu@${bot.username}: $warning@bla@")) {
            other.sendMessage(warning)
            other.sendText("Trade paused. Check the chatbox.", if (confirmation == null) 3431 else 3535)
        }
        val timer = Stopwatch.createStarted()
        while (open() && !responded() && timer.elapsed().seconds < 30) {
            waitFor(2.seconds) { !open() || responded() || timer.elapsed().seconds >= 30 }
        }
        if (open() && !responded()) bot.overlays.closeWindows()
    }
}
