import api.predef.*
import io.luna.game.event.impl.ServerLaunchEvent
import world.player.item.groundItem.GroundItemTask

/**
 * Start the expiration task.
 */
on(ServerLaunchEvent::class) {
    world.schedule(GroundItemTask())
}