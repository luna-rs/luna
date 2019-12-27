package world.player.item.groundItem

import api.predef.*
import io.luna.game.event.impl.ServerLaunchEvent

/**
 * Start the expiration task.
 */
on(ServerLaunchEvent::class) {
    world.schedule(GroundItemTask())
}