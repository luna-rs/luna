package engine.widget

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.CloseInterfaceEvent

/**
 * Closes the currently open interface.
 */
on(CloseInterfaceEvent::class, EventPriority.HIGH) {
    plr.interfaces.close()
}