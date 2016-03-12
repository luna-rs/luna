import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.Position

>>@[CommandEvent]("move") { (msg, plr) =>
  val args = msg.getArgs

  val x = args(0).toInt
  val y = args(1).toInt
  val z = if (args.length == 3) args(2).toInt else plr.getPosition.getZ

  plr.teleport(new Position(x, y, z));
}

