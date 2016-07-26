/*
 A plugin that adds functionality for withdrawing items from the bank as noted.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ButtonClickEvent


/* Withdraw items as unnoted. */
intercept_@[ButtonClickEvent](5387) { (msg, plr) => plr.attr("withdraw_as_note", false) }

/* Withdraw items as noted. */
intercept_@[ButtonClickEvent](5386) { (msg, plr) => plr.attr("withdraw_as_note", true) }