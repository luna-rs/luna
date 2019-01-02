import api.predef.*
import io.luna.game.event.impl.ServerLaunchEvent
import world.player.item.fill.SandSource
import world.player.item.fill.WaterSource

// TODO Needs testing!
on(ServerLaunchEvent::class) {
    WaterSource.register()
    SandSource.register()
}
