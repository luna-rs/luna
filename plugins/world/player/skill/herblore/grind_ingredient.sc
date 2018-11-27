import io.luna.game.action.{Action, ProducingAction}
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.mob.{Animation, Player}


/* Pestle and mortar item identifier. */
private val PESTLE_AND_MORTAR = 233

/* Animation to play when grinding ingredients. */
private val ANIMATION = new Animation(364)

/* A map of pre-grind identifiers to their grind identifiers. */
private val INGREDIENTS = Map(
  1973 -> 1975, // Chocolate dust
  243 -> 241, // Dragon scale dust
  5075 -> 6693, // Crushed nest
  237 -> 235 // Unicorn horn dust
)


/* An Action that will grind all ingredients in an inventory. */
private final class GrindAction(val plr: Player, val oldId: Int,
                                val newId: Int, var amount: Int) extends ProducingAction(plr, true, 2) {

  override def onProduce() = {
    val nextWord = if (newId == 6693) "a" else "some"
    plr.sendMessage(s"You grind the ${nameOfItem(oldId)} into $nextWord ${nameOfItem(newId)}.")

    plr.animation(ANIMATION)
    amount -= 1

    if (amount == 0) {
      interrupt()
    }
  }

  override def remove = Array(new Item(oldId))

  override def add = Array(new Item(newId))

  override def isEqual(other: Action[_]) = {
    other match {
      case action: GrindAction => oldId == action.oldId
      case _ => false
    }
  }
}

/* A dialogue that displays the item to grind. */
private final class GrindDialogue(val oldId: Int, val newId: Int) extends MakeItemDialogueInterface(newId) {
  override def makeItem(player: Player, id: Int, index: Int, forAmount: Int) = {
    player.submitAction(new GrindAction(player, oldId, id, forAmount))
  }
}


/* Perform a lookup and submit the 'GrindAction' if successful. */
private def grind(plr: Player, id: Int, evt: ItemOnItemEvent) = {
  INGREDIENTS.get(id).foreach { it =>
    plr.interfaces.open(new GrindDialogue(id, it))
    evt.terminate
  }
}


/* Intercept an item on item event, handle using ingredient with pestle and mortar. */
on[ItemOnItemEvent].run { msg =>
  if (msg.usedId == PESTLE_AND_MORTAR) {
    grind(msg.plr, msg.targetId, msg)
  } else if (msg.targetId == PESTLE_AND_MORTAR) {
    grind(msg.plr, msg.usedId, msg)
  }
}
