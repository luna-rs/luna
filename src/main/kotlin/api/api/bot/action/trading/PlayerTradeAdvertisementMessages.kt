package api.bot.action.trading

import api.bot.isRealBot
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerPrivacy.PrivacyMode
import io.luna.game.model.mob.bot.Bot
import io.luna.net.msg.out.GameChatboxMessageWriter
import io.luna.util.StringUtils
import io.luna.util.logging.LoggingSettings.FileOutputType
import java.nio.charset.StandardCharsets

/**
 * Delivers directed bot trade notices through the game-message packet on the game thread.
 *
 * Current sessions, Trade/compete access and both ignore lists are checked immediately before delivery. Public and
 * private chat settings do not control these notices. The client retains their sender for its Trade/compete filter.
 *
 * @author lare96
 */
object PlayerTradeAdvertisementMessages {

    /**
     * Tests the recipient's current Trade/compete setting without changing either player's friendships.
     */
    fun allowsTrade(bot: Bot, recipient: Player): Boolean = when (recipient.privacyOptions.trade) {
        PrivacyMode.ON -> true
        PrivacyMode.FRIENDS -> bot.usernameHash in recipient.friends
        else -> false
    }

    /**
     * Sends one complete counteroffer without truncating its price or quantity.
     *
     * @param text Trusted, formatted counteroffer text within the client's eighty-character message limit.
     * @return Whether a trade-notice packet was queued to the current human recipient.
     */
    fun counteroffer(bot: Bot, recipient: Player, text: String): Boolean {
        if (text.isBlank() || text.length > 80) return false
        return notice(bot, recipient, "@blu@${bot.username}: $text@bla@")
    }

    /**
     * Sends one complete formatted notice using the client's nonclickable trade-message category.
     *
     * @param text Trusted complete display text, including the sender's name and any color tags.
     * @return Whether the notice was queued without truncation to the current human recipient.
     */
    fun notice(bot: Bot, recipient: Player, text: String): Boolean {
        if (text.isBlank() || !bot.isRealBot() || recipient.isRealBot()) return false
        val senderHash = bot.usernameHash
        val recipientHash = recipient.usernameHash
        if (bot === recipient || senderHash == recipientHash || bot.state != EntityState.ACTIVE ||
            recipient.state != EntityState.ACTIVE) return false
        val world = bot.world
        if (world.getPlayer(senderHash).orElse(null) !== bot ||
            world.getPlayer(recipientHash).orElse(null) !== recipient ||
            !allowsTrade(bot, recipient) || recipientHash in bot.ignores ||
            senderHash in recipient.ignores) return false

        val message = "${bot.username}:$text:tradeinfo:"
        if (message.toByteArray(StandardCharsets.ISO_8859_1).size + 1 > 255 || '\n' in message) return false
        recipient.queue(GameChatboxMessageWriter(message))
        FileOutputType.CHAT.level?.let { level ->
            FileOutputType.CHAT.logger.log(level, "{} --> {}: {}", bot.username,
                StringUtils.decodeFromBase37(recipientHash), text)
        }
        return true
    }
}
