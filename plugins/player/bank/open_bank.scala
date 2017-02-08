/*
 A plugin that adds functionality for opening banks.

 SUPPORTS:
  -> Opening a variety of banks by first click.

 TODO:
  -> Add more bank identifiers.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent


/* Open the banking interface. */
onargs[ObjectFirstClickEvent](3193, 2213, 3095) { msg =>
  val plr = msg.plr
  plr.bank.open
}