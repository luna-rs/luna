import io.luna.game.event.impl.CommandEvent

>>@[CommandEvent]("mypos", RIGHTS_DEV) { (msg, plr) =>
  plr.sendMessage(s"Your current position is ${plr.position}.")
}
