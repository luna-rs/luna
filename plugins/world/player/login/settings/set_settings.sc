import io.luna.game.event.impl.LoginEvent

private val RUN_BUTTON = 173


/* Configure interface states. */
on[LoginEvent].run { msg =>
  val plr = msg.plr

  plr.sendConfig(RUN_BUTTON, if (plr.walking.isRunning) 1 else 0)
}