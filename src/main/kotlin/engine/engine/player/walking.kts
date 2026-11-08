package engine.player

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.WalkingEvent

/**
 * Adds the path from the client to the walking queue.
 */
on(WalkingEvent::class, EventPriority.HIGH) {
    val prepared = plannedPath
    if (prepared != null) {
        if (prepared.isForced && !plr.isDebugNoclipEnabled) {
            plr.walking.clear()
        } else {
            plr.walking.replacePlannedPath(prepared)
        }
    } else {
        plr.walking.clear()
        if (plr.isDebugNoclipEnabled) {
            plr.walking.replaceForcedPath(steps)
        } else {
            plr.walking.replacePath(steps)
        }
    }
}
