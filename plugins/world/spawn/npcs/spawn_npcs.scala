import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc


private def spawn(id: Int, x: Int, y: Int, z: Int = 0): Npc = {
  val npc = new Npc(ctx, id, new Position(x, y, z))
  world.getNpcs.add(npc)
  npc
}

on[ServerLaunchEvent].run { msg =>
  // NPC spawns go here.
}
