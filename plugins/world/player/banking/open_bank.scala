/*
 Adds functionality for opening banks.
*/

import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent


/* Open the banking interface. */
onargs[ObjectFirstClickEvent](3193, 2213, 3095) { msg =>
  val plr = msg.plr
  plr.bank.open
}