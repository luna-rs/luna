import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mobile.{Animation, ForcedMovement, Graphic}


>>@[CommandEvent]("music", RIGHTS_DEV) { (msg, plr) =>
  plr.sendMusic(msg.getArgs()(0).toInt)
}

>>@[CommandEvent]("interface", RIGHTS_DEV) { (msg, plr) =>
  plr.sendInterface(msg.getArgs()(0).toInt)
}

>>@[CommandEvent]("sound", RIGHTS_DEV) { (msg, plr) =>
  plr.sendSound(msg.getArgs()(0).toInt, 0, 0)
}

>>@[CommandEvent]("graphic", RIGHTS_DEV) { (msg, plr) =>
  plr.graphic(new Graphic(msg.getArgs()(0).toInt))
}

>>@[CommandEvent]("animation", RIGHTS_DEV) { (msg, plr) =>
  plr.animation(new Animation(msg.getArgs()(0).toInt))
}

>>@[CommandEvent]("force_movement", RIGHTS_DEV) { (msg, plr) =>
  plr.forceMovement(ForcedMovement.forceMoveY(plr, 5, 5))
}

>>@[CommandEvent]("player_npc", RIGHTS_DEV) { (msg, plr) =>
  plr.transform(msg.getArgs()(0).toInt)
}
