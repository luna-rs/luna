package engine.widget.settings

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.LoginEvent

/**
 * Configure and show saved settings.
 */
on(LoginEvent::class, EventPriority.NORMAL) {
    plr.varpManager.sendAllValues()
}