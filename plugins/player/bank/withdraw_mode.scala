/*
 A plugin that adds functionality for withdrawing items from the bank as noted.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ButtonClickEvent


/* Withdraw items as unnoted. */
on[ButtonClickEvent](5387) { _.plr.attr("withdraw_as_note", false) }

/* Withdraw items as noted. */
on[ButtonClickEvent](5386) { _.plr.attr("withdraw_as_note", true) }
