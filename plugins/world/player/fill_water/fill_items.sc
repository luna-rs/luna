import io.luna.game.action.{Action, ProducingAction}
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.{Animation, Player}


/* Filling animation. */
private val ANIMATION = new Animation(832)

/* Identifiers of various water sources. */
private val WATER_SOURCES = Set(153, 879, 880, 34579, 2864, 6232, 878, 884, 3359, 3485, 4004, 4005,
  5086, 6097, 8747, 8927, 9090, 6827, 3460)

/* Items that can be filled with water. */
private val FILLABLES = Map(
  1923 -> 1921, // Bowl
  229 -> 227, // Vial
  1925 -> 1929, // Bucket
  1980 -> 4458, // Cup
  1935 -> 1937 // Jug
)


/* Fills all items in an inventory with water. */
private final class FillAction(val evt: ItemOnObjectEvent, val oldId: Int,
                               val newId: Int, var amount: Int) extends ProducingAction(evt.plr, true, 2) {
  override def remove = Array(new Item(oldId))

  override def add = Array(new Item(newId))

  override def onProduce() = {
    mob.animation(ANIMATION)
    amount -= 1

    if(amount == 0) {
      interrupt()
    }
  }

  override def isEqual(other: Action[_]) = {
    other match {
      case action: FillAction if
      evt.objectId == action.evt.objectId &&
        oldId == action.oldId &&
        newId == action.newId => true

      case _ => false
    }
  }
}

/* Opens the "Make item" interface. */
private final class FillDialogue(val toFill: Int,
                                 val msg: ItemOnObjectEvent) extends MakeItemDialogueInterface(toFill) {
  override def makeItem(player: Player, id: Int, forAmount: Int) ={
    player.submitAction(new FillAction(msg, msg.itemId, id, forAmount))
  }
}


/* Intercept an item on object event, fill items if applicable. */
on[ItemOnObjectEvent].run { msg =>
  if (WATER_SOURCES.contains(msg.objectId)) {

    FILLABLES.get(msg.itemId).foreach { fillable =>
      msg.plr.interfaces.open(new FillDialogue(fillable, msg))
      msg.terminate
    }
  }
}