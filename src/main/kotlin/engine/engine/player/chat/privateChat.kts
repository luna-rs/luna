package engine.player.chat

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.PrivateChatEvent
import io.luna.net.msg.out.PrivateChatMessageWriter
import io.luna.util.StringUtils
import io.luna.util.logging.LoggingSettings.FileOutputType
import org.apache.logging.log4j.Logger

/**
 * An asynchronous logger that will handle private message logs.
 */
val logger: Logger = FileOutputType.PRIVATE_MESSAGE.logger

/**
 * Sends a private message.
 */
on(PrivateChatEvent::class, EventPriority.HIGH) {
    world.getPlayer(name).ifPresent {
        it.queue(PrivateChatMessageWriter(plr.usernameHash, message))
        logger.log(FileOutputType.PRIVATE_MESSAGE.level, "{} --> {}: {}", plr.username,
                   StringUtils.decodeFromBase37(name), unpackedMessage)
    }
}