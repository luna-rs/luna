package game.content.seasonalEvents

import api.predef.*
import api.predef.ext.schedule
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent

/**
 * Loads the persisted seasonal event data.
 */
on(ServerLaunchEvent::class) {
    taskPool.submit { SeasonalEventPersistence.load() }
}

/**
 * Starts the seasonal event scheduling task.
 *
 * - Updates seasonal event state and handles item drops.
 * - Saves persistent data to the disk when needed.
 */
on(ServerReadyEvent::class) {
    world.schedule(1) {
        SeasonalEventScheduler.update()
        SeasonalEventPersistence.save()
    }
}