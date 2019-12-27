package world.player.announcements

import api.predef.*
import io.luna.game.event.impl.ServerLaunchEvent

/**
 * Announcement broadcast interval.
 */
val intervalTicks = 1500 // 15 mins

/**
 * Messages that will be randomly announced.
 */
val messages = listOf(
        "Luna is a Runescape  server designed for the #317 protocol.",
        "Luna can be found on GitHub under luna-rs/luna",
        "Change these messages in the 'Announcements' plugin.",
        "Any bugs found using Luna should be reported to the GitHub page.",
        "Did you know? Chili peppers are awesome!")

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
    world.schedule(intervalTicks) { sendMessages() }
}