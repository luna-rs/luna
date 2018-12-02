import api.*
import io.luna.game.event.impl.ButtonClickEvent

/**
 * Start/stop running.
 */
on(ButtonClickEvent::class)
    .args(152)
    .run { it.plr.walking.isRunning = false }

on(ButtonClickEvent::class)
    .args(153)
    .run { it.plr.walking.isRunning = true }