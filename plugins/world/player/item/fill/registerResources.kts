import api.predef.*
import io.luna.game.event.server.ServerLaunchEvent
import world.player.item.fill.MilkResource
import world.player.item.fill.SandResource
import world.player.item.fill.WaterResource

/**
 * Registers all resources.
 */
on(ServerLaunchEvent::class) {
    WaterResource.register()
    SandResource.register()
    MilkResource.register()
}
