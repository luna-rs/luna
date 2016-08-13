/*
 A plugin that adds functionality for opening banks.

 SUPPORTS:
  -> Opening a variety of banks by first click.

 TODO:
  -> Add more bank identifiers.
  -> Deposit boxes.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent


/* If the object clicked is a bank, open the banking interface. */
intercept_@[ObjectFirstClickEvent](3193, 2213, 3095) { (msg, plr) =>
  plr.bank.open
}
