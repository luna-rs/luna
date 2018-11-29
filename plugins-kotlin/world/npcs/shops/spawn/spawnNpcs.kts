import api.*
import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc

/**
 * Spawns an npc.
 */
fun spawn(id: Int, x: Int, y: Int, z: Int = 0): Npc {
    val npc = Npc(ctx, id, Position(x, y, z))
    world.add(npc)
    return npc
}

on(ServerLaunchEvent::class).run {
    // NPC spawns go here.
}
