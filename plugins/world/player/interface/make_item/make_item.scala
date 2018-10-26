import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.inter.AmountInputInterface

import scala.collection.mutable


/* A model that runs the action based on the item's index and amount */
private class MakeItemAction(val amount: Int, var index: Int) {
  def run(plr: Player, inter: MakeItemDialogueInterface) = {
    if (amount == -1) {
      // Make <x> option.
      plr.interfaces.open(new AmountInputInterface() {
        override def onNumberInput(player: Player, number: Int) {
          inter.makeItemIndex(plr, index, number)
          plr.interfaces.close()
        }
      })
    } else {
      // Make specific amount option.
      inter.makeItemIndex(plr, index, amount)
      plr.interfaces.close()
    }
  }
}

/* A list of button ids and the indexes that they correspond to. */
private val BUTTON_LIST = List(
  // make1, make5, make10, makeX, index
  (8893, 8892, 8891, 8890, 0),
  (8874, 8873, 8872, 8871, 0),
  (8878, 8877, 8876, 8875, 1),
  (8889, 8888, 8887, 8886, 0),
  (8893, 8892, 8891, 8890, 1),
  (8897, 8896, 8895, 8894, 2),
  (8909, 8908, 8907, 8906, 0),
  (8913, 8912, 8911, 8910, 1),
  (8917, 8916, 8915, 8914, 2),
  (8921, 8920, 8919, 8918, 3),
  (8949, 8948, 8947, 8946, 0),
  (8953, 8952, 8951, 8950, 1),
  (8957, 8956, 8955, 8954, 2),
  (8961, 8960, 8959, 8958, 3),
  (8965, 8964, 8963, 8962, 4)
)

/* Maps every button to a MakeItemAction. */
private val BUTTON_MAP = {
  val map = new mutable.HashMap[Int, MakeItemAction]()
  BUTTON_LIST.foreach { buttons =>
    val (make1Id, make5Id, make10Id, makeXId, index) = buttons
    map(make1Id) = new MakeItemAction(1, index)
    map(make5Id) = new MakeItemAction(5, index)
    map(make10Id) = new MakeItemAction(10, index)
    map(makeXId) = new MakeItemAction(-1, index)
  }
  map.toMap
}

/* Runs the MakeItemAction. */
private def makeItem(evt: ButtonClickEvent, inter: MakeItemDialogueInterface, action: MakeItemAction) = {

  // Because 1 and 3 use the same interface, but different indexes.
  if (isDuplicate(evt.id)) {
    if (inter.getLength == 1) {
      action.index = 0
    } else if (inter.getLength == 3) {
      action.index = 1
    }
  }

  // Run the action.
  action.run(evt.plr, inter)
}

/* Determines if the argued id is a duplicate button. */
private def isDuplicate(id: Int) = id match {
  case 8893 | 8892 | 8891 | 8890 => true
  case _ => false
}

/* Get MakeItemAction from button id, if available. */
on[ButtonClickEvent] { msg =>
  try {
    val interfaces = msg.plr.interfaces
    val buttonAction = BUTTON_MAP.get(msg.id)

    buttonAction.foreach { action =>
      interfaces.getCurrentStandard.ifPresent({
        case inter: MakeItemDialogueInterface => makeItem(msg, inter, action)
        case _ => interfaces.close()
      })
      msg.terminate
    }
  } catch {
    case e: Exception => e.printStackTrace()
  }
}