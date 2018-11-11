import io.luna.game.event.impl.ButtonClickEvent


/* If the logout button is clicked, logout the player. */
on[ButtonClickEvent].
  args { 2458 }.
  run { _.plr.logout }
