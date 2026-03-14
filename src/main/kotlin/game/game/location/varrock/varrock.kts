package game.location.varrock

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.*
import java.util.*

on(ServerLaunchEvent::class) {
    // Bankers of west bank
    world.addNpc(494, 3187, 3446).defaultDirection = Optional.of(Direction.WEST)
    world.addNpc(494, 3187, 3444).defaultDirection = Optional.of(Direction.WEST)
    world.addNpc(494, 3187, 3442).defaultDirection = Optional.of(Direction.WEST)
    world.addNpc(494, 3187, 3438).defaultDirection = Optional.of(Direction.WEST)
    world.addNpc(494, 3187, 3436).defaultDirection = Optional.of(Direction.WEST)

    // Bankers of east bank
    world.addNpc(494, 3252, 3418).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 3253, 3418).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 3254, 3418).defaultDirection = Optional.of(Direction.NORTH)
    world.addNpc(494, 3256, 3418).defaultDirection = Optional.of(Direction.NORTH)

}