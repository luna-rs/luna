package world.player.privateChat

import api.predef.*
import io.luna.game.event.impl.PrivateChatEvent
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.PrivateChatMessageWriter

/**
 * Sends a private message.
 */
fun sendMessage(plr: Player, name: Long, msg: ByteArray) {
    world.getPlayer(name).ifPresent { it.queue(PrivateChatMessageWriter(plr.usernameHash, msg)) }
}

on(PrivateChatEvent::class) { sendMessage(plr, name, message) }