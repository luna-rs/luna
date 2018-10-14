import io.luna.game.event.impl.{LoginEvent, ServerLaunchEvent}
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc


private def spawn(id: Int, x: Int, y: Int, z: Int = 0): Npc = {
  val pos = new Position(x, y, z)
  world.add(new Npc(ctx, id, pos))
}

on[ServerLaunchEvent] { msg =>
  // NPC spawns go here.

}
