package world.item.globalSpawn

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Position

// Load spawn file on server launch.
on(ServerLaunchEvent::class) {
    val parser = ItemSpawnFileParser()
    // Items can be added programmatically.
    // Ie. 10,000 coins spawned at 3200,3200 that respawns 60 ticks after being picked up.
    // parser.add(PersistentGroundItem(995, 10_000, Position(3200, 3200), 60))
    // can also do world.add, same thing
    taskPool.execute(parser)
}