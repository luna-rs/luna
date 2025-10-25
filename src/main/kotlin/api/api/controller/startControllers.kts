package api.controller

import api.predef.*
import game.player.ControllerKeys
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/* Ensure that all controllers are initialized. */
on(ServerLaunchEvent::class) { ControllerKeys }
