package engine.player.chat

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.PrivateChatEvent

/**
 * Sends a private message.
 */
on(PrivateChatEvent::class, EventPriority.HIGH) {
    world.getPlayer(name).ifPresent {
        sendPrivateChat(plr, it, message, unpackedMessage)
    }
}
