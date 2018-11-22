import io.luna.game.event.impl.WidgetItemClickEvent
import io.luna.game.event.impl.WidgetItemClickEvent._
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface


/* Deposit an item. */
private def deposit(msg: WidgetItemClickEvent, amount: Int): Unit = {
  val plr = msg.plr
  val bank = plr.bank
  if (bank.isOpen) {
    amount match {
      case -1 => bank.deposit(msg.index, plr.inventory.computeAmountForId(msg.itemId))
      case _ => bank.deposit(msg.index, amount)
    }
  }
}

/* Withdraw an item. */
private def withdraw(msg: WidgetItemClickEvent, amount: Int): Unit = {
  val plr = msg.plr
  val bank = plr.bank
  if (bank.isOpen) {
    amount match {
      case -1 => bank.withdraw(msg.index, bank.computeAmountForId(msg.itemId))
      case _ => bank.withdraw(msg.index, amount)
    }
  } else if (plr.rights >= RIGHTS_DEV) {
    // For the "::search_item" command (it uses the banking interface).
    amount match {
      case -1 => plr.inventory.add(new Item(msg.itemId, "1,000,000".fInt))
      case _ => plr.inventory.add(new Item(msg.itemId, amount))
    }
  }
}


/* Withdraw/deposit 1. */
on[WidgetItemFirstClickEvent].
  args { 5064 }.
  run { deposit(_, 1) }

on[WidgetItemFirstClickEvent].
  args { 5382 }.
  run { withdraw(_, 1) }


/* Withdraw/deposit 5. */
on[WidgetItemSecondClickEvent].
  args { 5064 }.
  run { deposit(_, 5) }

on[WidgetItemSecondClickEvent].
  args { 5382 }.
  run { withdraw(_, 5) }


/* Withdraw/deposit 10. */
on[WidgetItemThirdClickEvent].
  args { 5064 }.
  run { deposit(_, 10) }

on[WidgetItemThirdClickEvent].
  args { 5382 }.
  run { withdraw(_, 10) }


/* Withdraw/deposit all. */
on[WidgetItemFourthClickEvent].
  args { 5064 }.
  run { deposit(_, -1) }

on[WidgetItemFourthClickEvent].
  args { 5382 }.
  run { withdraw(_, -1) }


/* Withdraw/deposit (x). */
on[WidgetItemFifthClickEvent].
  args { 5064 }.
  run { msg =>
    msg.plr.interfaces.open(new AmountInputInterface {
      override def onAmountInput(player: Player, value: Int): Unit =
        deposit(msg, value)
    })
  }

on[WidgetItemFifthClickEvent].
  args { 5382 }.
  run { msg =>
    msg.plr.interfaces.open(new AmountInputInterface {
      override def onAmountInput(player: Player, value: Int): Unit =
        withdraw(msg, value)
    })
  }