package world.item.globalSpawn

import api.predef.on
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

// Load spawn file on server launch.
on(ServerLaunchEvent::class) {
    val parser = ItemSpawnFileParser()
    // Items can be added programmatically.
    // Ie. 10,000 coins spawned at 3200,3200 that respawn 60 ticks after being picked up.
    // parser.add(PersistentGroundItem(995, 10_000, Position(3222, 3222), 60))
    taskPool.execute(parser)
}