package api.bot.injectors

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Register all speech injectors.
 */
on(ServerLaunchEvent::class) {
    val speechManager = world.botManager.speechManager
    speechManager.addInjector(TrainingSpeechInjector)
}