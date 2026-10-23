package game.skill.fishing.fishingSpot

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

// Begin processing for moving fishing spots. Spots that have no FishingSpotArea never move.
on(ServerLaunchEvent::class) {
    world.schedule(FishingSpotHandler)
}
