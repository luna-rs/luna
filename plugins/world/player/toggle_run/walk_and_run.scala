import io.luna.game.event.impl.ButtonClickEvent


/* Stop running. */
onargs[ButtonClickEvent](152) { _.plr.walking.setRunning(false) }

/* Start running. */
onargs[ButtonClickEvent](153) { _.plr.walking.setRunning(true) }