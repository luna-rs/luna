import api.*
import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.mob.Player


/* Announcement broadcast interval. */
private val tickInterval = 1500 // 15 mins

/* Messages that will be randomly announced. */
private val messagesToSend = listOf("Luna is a Runescape private server for the #317 protocol.",
        "Luna can be found on GitHub under luna-rs/luna",
        "Change these messages in /plugins/world/announcements/announcements.sc",
        "Any bugs found using Luna should be reported to the GitHub page.")

/* A filter for players that will receive the announcement. */
private val playerFilter: (Player) -> Boolean = { it.rights < RIGHTS_ADMIN }

fun sendMessages() {
    world.players.filter(playerFilter).forEach {
        it.sendMessage(messagesToSend.random())
    }
}

on(ServerLaunchEvent::class).run {
    world.scheduleForever(tickInterval) {
        sendMessages()
    }
}