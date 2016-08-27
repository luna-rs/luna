/*
 A plugin that adds functionality for opening banks.

 SUPPORTS:
  -> Opening a variety of banks by first click.

 TODO:
  -> Add more bank identifiers.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent


/* If the object clicked is a bank, open the banking interface. */
on[ObjectFirstClickEvent](3193, 2213, 3095) { _.plr.bank.open }