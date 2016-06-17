import io.luna.game.event.impl.CommandEvent

>>@[CommandEvent]("bank", RIGHTS_DEV) { (msg, plr) =>
  plr.bank.open
}
