package world.player.privateChat

import api.predef.*
import io.luna.game.event.impl.PrivateChatEvent
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.PrivateChatMessageWriter

/**
 * Sends a message.
 */
fun sendMessage(plr: Player, name: Long, msg: ByteArray) {
    world.getPlayer(name)
        .ifPresent { it.queue(PrivateChatMessageWriter(plr.usernameHash, msg)) }
}

/**
 * Listens for a  chat event, and sends the message.
 */
on(PrivateChatEvent::class) { sendMessage(plr, name, message) }