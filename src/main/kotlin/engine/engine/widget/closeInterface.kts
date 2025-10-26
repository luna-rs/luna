package engine.widget

import api.predef.*
import io.luna.game.event.impl.CloseInterfaceEvent

on(CloseInterfaceEvent::class) {
    plr.interfaces.close()
}