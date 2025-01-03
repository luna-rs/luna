package world.player.announcements

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import java.time.Duration

/**
 * Announcement broadcast interval.
 */
val interval = Duration.ofMinutes(15)

/**
 * Messages that will be randomly announced.
 */
val messages = listOf(
        "Luna is a Runescape  server designed for the #377 protocol.",
        "Luna can be found on GitHub under luna-rs/luna",
        "Change these messages in the 'Announcements' plugin.",
        "Any bugs found using Luna should be reported to the GitHub page.",
        "You should never use the RSA keys that come with Luna. Generate new ones!")

/**
 * Sends one global message, randomly selected from the list of messages.
 */
fun sendMessages() {
    world.players.stream()
        .filter { it.rights < RIGHTS_ADMIN }
        .forEach { it.sendMessage(messages.random()) }
}

/**
 * Schedules a task that will send global messages.
 */
on(ServerLaunchEvent::class) {
    world.schedule(interval) { sendMessages() }
}