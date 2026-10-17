package engine

import api.predef.*
import api.predef.ext.*
import io.luna.Luna
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent

// Schedule a task that auto-saves every <x> minutes.
on(ServerReadyEvent::class) {
    val delay = Luna.settings().game().autosaveMinutes()
    if (delay > 0) {
        world.schedule(delay, false) {
            world.persistenceService.saveAll()
        }
    }
}