package engine.player

import api.attr.Attr
import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.FocusChangedEvent
import io.luna.game.event.impl.PlayerTimeoutEvent
import io.luna.game.model.mob.Player

/**
 * The idle timer attribute.
 */
val Player.idleTimer by Attr.timer()

/**
 * Logs the player out after some inactivity.
 */
on(PlayerTimeoutEvent::class, EventPriority.HIGH) {
    if (!plr.idleTimer.isRunning) {
        plr.idleTimer.start()
    }
    if (plr.idleTimer.duration.toMinutes() >= 5) {
        // TODO Ensure not in combat.
        plr.logout()
    }
}

/**
 * Reset idle timer if player focuses on game screen window.
 */
on(FocusChangedEvent::class) {
    if (isFocused) {
        plr.idleTimer.reset()
    }
}