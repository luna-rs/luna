/*
 A plugin related to the Herblore skill that adds functionality for grinding potion ingredients.

 SUPPORTS:
  -> Grinding all secondary potion ingredients.
  -> Grinding all ingredients in the inventory.

 AUTHOR: lare96
*/

import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
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
private final class GrindAction(plr: Player, oldId: Int, newId: Int) extends ProducingAction(plr, true, 2) {

  override def onProduce() = {
    val nextWord = if (newId == 6693) "a" else "some"
    plr.sendMessage(s"You grind the ${ nameOfItem(oldId) } into $nextWord ${ nameOfItem(newId) }.")

    plr.animation(ANIMATION)
  }

  override def remove = Array(new Item(oldId))
  override def add = Array(new Item(newId))
}


/* Perform a lookup and submit the 'GrindAction' if successful. */
private def grind(plr: Player, id: Int, evt: ItemOnItemEvent) = {
  INGREDIENTS.get(id).foreach { it =>
    plr.submitAction(new GrindAction(plr, id, it))
    evt.terminate
  }
}


/* Intercept an item on item event, handle using ingredient with pestle and mortar. */
on[ItemOnItemEvent] { msg =>
  if (msg.usedId == PESTLE_AND_MORTAR) {
    grind(msg.plr, msg.targetId, msg)
  } else if (msg.targetId == PESTLE_AND_MORTAR) {
    grind(msg.plr, msg.usedId, msg)
  }
}
