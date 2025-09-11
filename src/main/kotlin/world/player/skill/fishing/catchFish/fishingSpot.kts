package world.player.skill.fishing.catchFish

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Position

// Begin processing for rotating fishing spots.
on(ServerLaunchEvent::class) {
    world.schedule(FishingSpotHandler)
}

/* Enable rotating locations for fishing spots here. If a fishing spot is
    not manually added here they will not rotate locations. */

// Just a test and example.
/*FishingSpotHandler.add(spawn = true) {
    id = 316
    home = Position(3222, 3222)
    away += Position(3222, 3221)
}*/