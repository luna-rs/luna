import io.luna.game.event.impl.LoginEvent

/* Configure interface states. */
on[LoginEvent] { msg =>
  val plr = msg.plr

  plr.sendConfig(173, if (plr.walking.isRunning) 1 else 0)
}