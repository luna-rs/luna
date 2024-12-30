package api.controller

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/* Ensure that all controllers are initialized. */
on(ServerLaunchEvent::class) { Controllers }
