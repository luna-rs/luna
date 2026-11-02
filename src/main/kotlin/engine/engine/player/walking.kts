package engine.player

import api.predef.*
import io.luna.Luna
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.WalkingEvent

/**
 * Adds the path from the client to the walking queue.
 */
on(WalkingEvent::class, EventPriority.HIGH) {
    plr.walking.clear()
    if (Luna.settings().game().betaMode()) {
        plr.walking.replaceForcedPath(steps)
    } else {
        plr.walking.replacePath(steps)
    }
}
