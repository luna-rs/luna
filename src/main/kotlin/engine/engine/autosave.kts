package engine

import api.predef.*
import api.predef.ext.*
import io.luna.Luna
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes

// Schedule a task that auto-saves every <x> minutes.
on(ServerReadyEvent::class) {
    var previous: CompletableFuture<Void> = CompletableFuture.completedFuture(null)
    val delay = Luna.settings().game().autosaveMinutes()
    if (delay > 0) {
        world.schedule(delay.minutes, false) {
            if (previous.isDone) {
                previous = world.persistenceService.saveAll()
            }
        }
    }
}