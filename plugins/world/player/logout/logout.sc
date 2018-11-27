import io.luna.game.event.impl.{ButtonClickEvent, LogoutEvent}


/* On logout, close interfaces. */
on[LogoutEvent].run { _.plr.interfaces.close }

/* Log the player out if the logout button is clicked. */
on[ButtonClickEvent].
  args { 2458 }.
  run { _.plr.logout }