/*
 Adds functionality for withdrawing items from the bank as noted.
*/

import io.luna.game.event.impl.ButtonClickEvent

/* Sets the withdraw mode if the banking interface is open. */
private def setWithdrawMode(msg: ButtonClickEvent, value: Boolean): Unit = {
  val plr = msg.plr
  if (plr.bank.isOpen) {
    plr.attr("withdraw_as_note", value)
  }
}

/* Withdraw items as unnoted. */
onargs[ButtonClickEvent](5387) {
  setWithdrawMode(_, false)
}

/* Withdraw items as noted. */
onargs[ButtonClickEvent](5386) {
  setWithdrawMode(_, true)
}
