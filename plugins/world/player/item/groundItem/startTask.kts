import io.luna.game.event.impl.ServerLaunchEvent

/**
 * Start the expiration task.
 */
on(ServerLaunchEvent::class) {
    world.schedule(GroundItemTask())
}