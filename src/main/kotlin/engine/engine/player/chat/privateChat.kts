package engine.player.chat

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.PrivateChatEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerPrivacy.PrivacyMode
import io.luna.net.msg.out.PrivateChatMessageWriter
import io.luna.util.StringUtils
import io.luna.util.logging.LoggingSettings.FileOutputType
import org.apache.logging.log4j.Logger

/**
 * An asynchronous logger that will handle private message logs.
 */
val logger: Logger = FileOutputType.PRIVATE_MESSAGE.logger

/**
 * Whether [recipient]'s ignore list and private chat setting let [sender] message them. The same rule decides whether
 * [sender]'s friends list shows [recipient] online.
 */
fun canMessage(sender: Player, recipient: Player): Boolean = when {
    recipient.ignores.contains(sender.usernameHash) -> false
    recipient.privacyOptions.privateChat == PrivacyMode.FRIENDS -> recipient.friends.contains(sender.usernameHash)
    else -> recipient.privacyOptions.privateChat != PrivacyMode.OFF
}

/**
 * Sends a private message.
 */
on(PrivateChatEvent::class, EventPriority.HIGH) {
    world.getPlayer(name).filter { canMessage(plr, it) }.ifPresent {
        it.queue(PrivateChatMessageWriter(plr.usernameHash, message, plr.rights))
        logger.log(FileOutputType.PRIVATE_MESSAGE.level, "{} --> {}: {}", plr.username,
                   StringUtils.decodeFromBase37(name), unpackedMessage)
    }
}
