package engine.spawn

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

// Load spawn file on server launch.
on(ServerLaunchEvent::class) {
    taskPool.execute(ItemSpawnFileParser())
    taskPool.execute(NpcSpawnFileParser())
}