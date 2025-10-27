package engine.player.chat

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.ChatEvent
import io.luna.game.model.mob.block.Chat
import io.luna.util.logging.LoggingSettings.FileOutputType

/**
 * An asynchronous logger that will handle chat logs.
 */
val logger = FileOutputType.CHAT.logger

/**
 * The `CHAT` logging level.
 */
val chat = FileOutputType.CHAT.level

/**
 * Sends the chat update block.
 */
on(ChatEvent::class, EventPriority.HIGH) {
    plr.chat(Chat(message, color, effect))
    logger.log(chat, "{}: {}", plr.username, unpackedMessage)
}