import api.*
import io.luna.game.event.impl.ButtonClickEvent

/**
 * Start/stop running.
 */
on(ButtonClickEvent::class)
    .args(152)
    .run { it.plr.walkingQueue.isRunning = false }

on(ButtonClickEvent::class)
    .args(153)
    .run { it.plr.walkingQueue.isRunning = true }