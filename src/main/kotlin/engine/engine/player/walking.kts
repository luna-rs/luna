package engine.player

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.WalkingEvent
import io.luna.game.event.impl.WalkingEvent.WalkingOrigin

/**
 * Adds the path from the client to the walking queue.
 */
on(WalkingEvent::class, EventPriority.HIGH) {
    if (origin == WalkingOrigin.MINIMAP || origin == WalkingOrigin.MAIN_SCREEN) {
        plr.resetInteractingWith()
        plr.resetInteractionTask()
    }
    plr.walking.clear()
    plr.walking.addPath(steps)
}