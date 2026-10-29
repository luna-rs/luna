package engine.obj

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection

// Deletes pesky overworld gates/doors/etc. that mess with bot pathing.
// THIS IS TEMPORARY AND WILL BE DELETED BEFORE 0.5.0 (maybe change it to always keeping these doors/gates open?).
on(ServerLaunchEvent::class) {
    val positions = listOf(

        // Edgeville dungeon brass key door.
        Position(3115, 3450),

        // Barbarian village doors.
        Position(3082, 3426),
        Position(3098, 3426),

        // Theiving chests.
        Position(3190, 3957), // 10 coin

        // HAM cult area door.
        Position(3158, 9640)
    )

    for (position in positions) {
        world.objects.removeFromPosition(position) { true }
    }
}

// Temp objects spawns (should be done from cache).
on(ServerLaunchEvent::class) {
    // Spinning wheel near ::home.
    world.addObject(id = 2644,
                    x = 3208,
                    y = 3415,
                    z = 0,
                    direction = ObjectDirection.NORTH)
}