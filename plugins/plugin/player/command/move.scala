import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.Position

>>@[CommandEvent]("move") { (msg, plr) =>
  val x = msg.getArgs(0)
  val y = msg.getArgs(1)
  val z = if (msg.getArgs.length == 3) msg.getArgs(2) else plr.getPosition.getZ

  plr.teleport(new Position(x, y, z));
}

