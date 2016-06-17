import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mobile.{Animation, Graphic}

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