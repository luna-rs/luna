import io.luna.game.event.impl.CommandEvent

>>@[CommandEvent]("npc", RIGHTS_DEV) { (msg, plr) =>
  val args = msg.getArgs
  plr.getWorld.addNpc(args(0).toInt, plr.position)
}

>>@[CommandEvent]("player_npc", RIGHTS_DEV) { (msg, plr) =>
  val id = msg.getArgs()(0).toInt
  plr.transform(id)
}