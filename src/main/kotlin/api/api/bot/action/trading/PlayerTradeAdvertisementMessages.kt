package api.bot.action.trading

import api.bot.isRealBot
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerPrivacy.PrivacyMode
import io.luna.game.model.mob.bot.Bot
import io.luna.net.codec.ByteMessage
import io.luna.net.msg.out.PrivateChatMessageWriter
import io.luna.util.StringUtils
import io.luna.util.logging.LoggingSettings.FileOutputType

/**
 * Delivers server-generated counteroffers through the normal outbound private-message packet on the game thread.
 *
 * Current sessions, public PM access and both ignore lists are checked immediately before delivery. This helper does
 * not submit manual client PM input or change friendships; accepted asking prices need no private message.
 *
 * @author lare96
 */
object PlayerTradeAdvertisementMessages {

    /**
     * Sends one complete counteroffer without truncating its price or quantity.
     *
     * @param text Trusted, formatted counteroffer text within the client's eighty-character message limit.
     * @return Whether a private-message packet was queued to the current human recipient.
     */
    fun counteroffer(bot: Bot, recipient: Player, text: String): Boolean {
        if (text.isBlank() || text.length > 80 || !bot.isRealBot() || recipient.isRealBot()) return false
        val senderHash = bot.usernameHash
        val recipientHash = recipient.usernameHash
        if (bot === recipient || senderHash == recipientHash || bot.state != EntityState.ACTIVE ||
            recipient.state != EntityState.ACTIVE) return false
        val world = bot.world
        if (world.getPlayer(senderHash).orElse(null) !== bot ||
            world.getPlayer(recipientHash).orElse(null) !== recipient ||
            recipient.privacyOptions.privateChat != PrivacyMode.ON || recipientHash in bot.ignores ||
            senderHash in recipient.ignores) return false

        val packed = ByteMessage.raw()
        val bytes = try {
            StringUtils.packText(text, packed)
            packed.getBytes(packed.buffer.readableBytes())
        } finally {
            packed.releaseAll()
        }
        recipient.queue(PrivateChatMessageWriter(senderHash, bytes, bot.rights))
        FileOutputType.PRIVATE_MESSAGE.level?.let { level ->
            FileOutputType.PRIVATE_MESSAGE.logger.log(level, "{} --> {}: {}", bot.username,
                StringUtils.decodeFromBase37(recipientHash), text)
        }
        return true
    }
}
