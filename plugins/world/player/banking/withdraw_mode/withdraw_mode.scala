/*
 Adds functionality for withdrawing items from the bank as noted.
*/

import io.luna.game.event.impl.ButtonClickEvent


/* Withdraw items as unnoted. */
onargs[ButtonClickEvent](5387) { _.plr.attr("withdraw_as_note", false) }

/* Withdraw items as noted. */
onargs[ButtonClickEvent](5386) { _.plr.attr("withdraw_as_note", true) }
