package world.npc.globalSpawn

import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

// Load spawn file on server launch.
on(ServerLaunchEvent::class) {
    val parser = NpcSpawnFileParser()
    // NPCs can be added programmatically.
    // Ie. A man spawned at 3200,3200 that respawns 60 ticks after being killed, and wanders within a 2 tile radius.
    // world.add(PersistentNpc(1, Position(3200, 3200), 60, 2))
    taskPool.execute(parser)
}