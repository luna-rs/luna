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
    // An open id can be shared by several doors, but its click handler must only be registered once.
    val registered = HashSet<Int>()
    files.forEach { (kind, file) ->
        val path = Paths.get("data", "game", "world", "doors", file)
        taskPool.execute(DoorFileParser(path, kind) { types ->
            types.forEach { type ->
                for (id in listOf(type.closed, type.open)) {
                    if (registered.add(id)) {
                        object1(id) {
                            handleDoorClick(gameObject, plr)
                        }
                    }
                }
            }
        })
    }
}

fun handleDoorClick(gameObject: GameObject, plr: Player) {
    Doors.toggle(world, plr, gameObject)
}
