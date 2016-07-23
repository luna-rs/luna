/*
 Plugin for logging out, supports:
  -> Logging the player out when the logout button is clicked

 TODO:
   -> Disable the logout button during combat and 10 seconds after combat
*/

import io.luna.game.event.impl.ButtonClickEvent


/* If the logout button is clicked, logout the player. */
>>@[ButtonClickEvent](2458) { (msg, plr) => plr.logout }
