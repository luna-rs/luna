package game.location.falador

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.*
import java.util.*

on(ServerLaunchEvent::class) {
    // Bankers of west bank
    world.addNpc(494, 2945, 3366).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 2946, 3366).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 2947, 3366).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 2948, 3366).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 2949, 3366).defaultDirection = Optional.of(Direction.NORTH)

    // Bankers of east bank
    world.addNpc(494, 3010, 3353).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 3011, 3353).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 3012, 3353).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 3013, 3353).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 3014, 3353).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 3015, 3353).defaultDirection = Optional.of(Direction.NORTH)
}