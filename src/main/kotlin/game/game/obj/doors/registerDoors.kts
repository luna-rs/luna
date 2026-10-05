package game.obj.doors

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.*
import io.luna.game.model.`object`.*
import java.nio.file.Paths

// Load the door files on server launch. Handlers are registered once each file has been parsed, on the game thread.
on(ServerLaunchEvent::class) {
    val files = mapOf(Doors.Kind.SINGLE to "doors.json",
                      Doors.Kind.DOUBLE to "double_doors.json",
                      Doors.Kind.GATE to "gates.json",
                      Doors.Kind.CURTAIN to "curtains.json")
    files.forEach { (kind, file) ->
        val path = Paths.get("data", "game", "world", "doors", file)
        taskPool.execute(DoorFileParser(path, kind) { types ->
            types.forEach { type ->
                object1(type.closed) {
                    handleDoorClick(gameObject, plr)
                }
                object1(type.open) {
                    handleDoorClick(gameObject, plr)
                }
            }
        })
    }
}

fun handleDoorClick(gameObject: GameObject, plr: Player) {
    Doors.toggle(world, plr, gameObject)
}
