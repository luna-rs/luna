package engine.player.chat

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerPrivacy.PrivacyMode
import io.luna.net.msg.out.PrivateChatMessageWriter
import io.luna.util.logging.LoggingSettings.FileOutputType

/**
 * Queues a native private message if the recipient's ignore list and private chat setting allow the sender.
 *
 * @return Whether the message was queued.
 */
internal fun sendPrivateChat(sender: Player, recipient: Player, message: ByteArray, unpackedMessage: String): Boolean {
    val senderHash = sender.usernameHash
    if (senderHash in recipient.ignores) return false
    when (recipient.privacyOptions.privateChat) {
        PrivacyMode.OFF -> return false
        PrivacyMode.FRIENDS -> if (senderHash !in recipient.friends) return false
        else -> Unit
    }

    recipient.queue(PrivateChatMessageWriter(senderHash, message, sender.rights))
    val log = FileOutputType.PRIVATE_MESSAGE
    log.level?.let { log.logger.log(it, "{} --> {}: {}", sender.username, recipient.username, unpackedMessage) }
    return true
}
