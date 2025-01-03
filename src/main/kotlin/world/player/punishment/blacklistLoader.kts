package world.player.punishment

import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.common.net.InetAddresses
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.util.parser.NewLineFileParser

/**
 * Loads the IP blacklist file on startup.
 */
on(ServerLaunchEvent::class) {
    taskPool.execute(object : NewLineFileParser(PunishmentHandler.IP_BANS) {
        override fun onCompleted(tokenObjects: ImmutableList<String>) {
            tokenObjects.stream().filter { InetAddresses.isInetAddress(it) }.forEach {
                server.channelFilter.addToBlacklist(it)
            }
        }
    })
}
