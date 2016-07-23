/*
 Plugin for banking, supports:
   -> Withdrawing as item/note buttons
   -> Opening banks by first click

 TODO:
   -> Bank pins
*/

import io.luna.game.event.impl.{ButtonClickEvent, ObjectFirstClickEvent}


/* A set of identifiers for bank objects. */
private val BANKS = Set(3193, 2213)


/* If the object clicked is a bank, open the banking interface. */
>>[ObjectFirstClickEvent] { (msg, plr) =>
  if (BANKS.contains(msg.getId)) {
    plr.bank.open
  }
}

/* If we're clicking the withdraw as item button. */
>>@[ButtonClickEvent](5387) { (msg, plr) => plr.attr("withdraw_as_note", false) }

/* If we're clicking the withdraw as note button. */
>>@[ButtonClickEvent](5386) { (msg, plr) => plr.attr("withdraw_as_note", true) }