package engine.combat.death

import api.combat.death.DeathHookHandler
import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Ensures we have default death hooks.
 */
on(ServerLaunchEvent::class) {
    if (DeathHookHandler.defaultNpcHook == null) {
        logger.warn("No default NPC hook found!")
    } else if (DeathHookHandler.defaultPlayerHook == null) {
        logger.warn("No default Player hook found!")
    }
}