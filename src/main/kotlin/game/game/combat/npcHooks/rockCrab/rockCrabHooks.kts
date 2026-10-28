package game.combat.npcHooks.rockCrab

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

on(ServerLaunchEvent::class) {
    world.schedule(1) { RockCrabs.process() }
}
