import io.luna.game.event.impl.CommandEvent

>>@[CommandEvent]("npc", RIGHTS_DEV) { (msg, plr) =>
  val args = msg.getArgs
  world.addNpc(args(0).toInt, plr.position)
}

>>@[CommandEvent]("object", RIGHTS_DEV) { (msg, plr) =>
  // TODO: Once object placement system is done
}
