package world.location.draynorVillage

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.*
import java.util.*

on(ServerLaunchEvent::class) {
    // Bankers
    world.addNpc(494, 3090, 3245).defaultDirection = Optional.of(Direction.EAST)
    world.addNpc(494, 3090, 3243).defaultDirection = Optional.of(Direction.EAST)
    world.addNpc(494, 3090, 3242).defaultDirection = Optional.of(Direction.EAST)

    // Dark wizards
    world.addNpc(174, 3086, 3238)
    world.addNpc(174, 3084, 3235)

    // Fishing spots for shrimp
    world.addNpc(316, 3085, 3230)
    world.addNpc(316, 3086, 3227)
}