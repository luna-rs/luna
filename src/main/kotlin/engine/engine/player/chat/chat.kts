package engine.player.chat

import api.predef.*
import io.luna.game.event.impl.PrivateChatEvent
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.PrivateChatMessageWriter
import io.luna.util.StringUtils
import io.luna.util.logging.LoggingSettings.FileOutputType

/**
 * An asynchronous logger that will handle private message logs.
 */
val logger = FileOutputType.PRIVATE_MESSAGE.logger

/**
 * The `PRIVATE_MESSAGE` logging level.
 */
val PRIVATE_MESSAGE = FileOutputType.PRIVATE_MESSAGE.level

/**
 * Sends a private message.
 */
fun sendMessage(plr: Player, name: Long, msg: ByteArray, unpackedMsg: String) {
    world.getPlayer(name).ifPresent {
        it.queue(PrivateChatMessageWriter(plr.usernameHash, msg))
        logger.log(PRIVATE_MESSAGE, "{} --> {}: {}", plr.username, StringUtils.decodeFromBase37(name), unpackedMsg)
    }
}

on(PrivateChatEvent::class) { sendMessage(plr, name, message, unpackedMessage) }