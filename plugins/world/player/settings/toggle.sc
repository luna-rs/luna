import io.luna.game.event.impl.ButtonClickEvent


/* Start/stop running. */
on[ButtonClickEvent].
  args { 152 }.
  run { _.plr.walking.setRunning(false) }

on[ButtonClickEvent].
  args { 153 }.
  run { _.plr.walking.setRunning(true) }