import io.luna.game.event.impl.CommandEvent

>>@[CommandEvent]("player_npc", RIGHTS_DEV) { (msg, plr) =>
  val id = msg.getArgs()(0).toInt
  plr.transform(id)
}

