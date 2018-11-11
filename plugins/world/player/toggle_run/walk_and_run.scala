import io.luna.game.event.impl.ButtonClickEvent


/* Stop running. */
on[ButtonClickEvent].
  args { 152 }.
  run { _.plr.walking.setRunning(false) }

/* Start running. */
on[ButtonClickEvent].
  args { 153 }.
  run { _.plr.walking.setRunning(true) }