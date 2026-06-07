package engine.obj

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Position
import io.luna.game.model.`object`.ObjectDirection

// Deletes pesky overworld gates/doors/etc. that mess with bot pathing.
// THIS IS TEMPORARY AND WILL BE DELETED BEFORE 0.5.0 (maybe change it to always keeping these doors/gates open?)
on(ServerLaunchEvent::class) {
    val positions = listOf(

        // Falador -> Taverly gates.
        Position(2935, 3450),
        Position(2935, 3451),

        // King black dragon gates.
        Position(3008, 3850),
        Position(3008, 3849),

        // Deep wilderness red dragons.
        Position(3201, 3856),
        Position(3202, 3856),

        // Al Kharid gates.
        Position(3268, 3227),
        Position(3268, 3228),

        // Edgeville dungeon brass key door.
        Position(3115, 3450),

        // Edgeville dungeon mine gates.
        Position(3145, 9870),
        Position(3145, 9871),

        // Barbarian village doors.
        Position(3082, 3426),
        Position(3098, 3426),

        // North Falador chaos temple door.
        Position(2941, 3517),

        // Theiving chests.
        Position(3190, 3957), // 10 coin
        Position(2641, 3424), // steel arrowtips
        Position(2565, 3356), // blood rune

        // Lumbridge river goblins door.
        Position(3246, 3244),

        // Deep wilderness entrances.
        Position(3225, 3904),
        Position(3224, 3904),
        Position(3337, 3896),
        Position(3336, 3896),
        Position(2948, 3904),
        Position(2947, 3904),

        // Lumbridge cow and chicken pen gates.
        Position(3253, 3266),
        Position(3253, 3267),

        Position(3236, 3295),
        Position(3236, 3296),

        // Al-kharid palace
        Position(3287, 3172),
        Position(3287, 3171),
        Position(3292, 3167),
        Position(3293, 3167),
        Position(3298, 3172),
        Position(3298, 3171),
    )

    for (position in positions) {
        world.objects.removeFromPosition(position) { true }
    }
}

// Temp objects spawns (should be done from cache)

/**
 * Spawn spinning wheel near home area.
 */
on(ServerLaunchEvent::class) {
    world.addObject(id = 2644,
                    x = 3208,
                    y = 3415,
                    z = 0,
                    direction = ObjectDirection.NORTH)
}