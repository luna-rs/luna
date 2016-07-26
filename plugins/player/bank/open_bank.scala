/*
 A plugin that adds functionality for opening banks.

 SUPPORTS:
  -> Opening a variety of banks by first click.

 TODO:
  -> Add more bank identifiers.
  -> Deposit boxes.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ObjectFirstClickEvent


/* A set of identifiers for bank objects. */
private val BANKS = Set(3193, 2213, 3095)


/* If the object clicked is a bank, open the banking interface. */
intercept[ObjectFirstClickEvent] { (msg, plr) =>
  if (BANKS.contains(msg.getId)) {
    plr.bank.open
  }
}
