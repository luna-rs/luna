package engine.player

import api.attr.Attr
import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.FocusChangedEvent
import io.luna.game.event.impl.PlayerTimeoutEvent
import io.luna.game.model.mob.Player

/**
 * The idle timer attribute.
 */
val Player.idleTimer by Attr.timer()

/**
 * Determines if [plr] has an active action significant enough to keep them online.
 */
private fun hasSignificantAction(plr: Player): Boolean {
    for (action in plr.actions.getAll<Action<*>>()) {
        when(action.actionType) {
            ActionType.NORMAL, ActionType.STRONG, ActionType.WEAK -> return true
            else -> continue
        }
    }
    return false
}

/**
 * Logs the player out after some inactivity.
 */
on(PlayerTimeoutEvent::class, EventPriority.HIGH) {
    if (plr.controllers.primary.isSpectator || hasSignificantAction(plr)) {
        plr.idleTimer.reset()
        return@on
    }
    if (!plr.idleTimer.isRunning) {
        plr.idleTimer.start()
    }
    if (plr.idleTimer.duration.toMinutes() >= 5 && !plr.combat.inCombat()) {
        plr.logout(false)
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
