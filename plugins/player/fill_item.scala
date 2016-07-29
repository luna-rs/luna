/*
 A plugin that adds functionality for filling specific items with water, using water sources.

 SUPPORTS:
  -> All fillable items.
  -> A variety of water sources.

 TODO:
   -> Add more water sources.

 AUTHOR: lare96
*/

import io.luna.game.action.ProducingSkillAction
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.{Animation, Player}


/* Animation to play when filling items. */
private val ANIMATION = new Animation(832)

/* Object identifiers of various water sources. */
private val WATER_SOURCES = Set(153, 879, 880, 34579, 2864, 6232, 878, 884, 3359, 3485, 4004, 4005,
  5086, 6097, 8747, 8927, 9090, 6827, 3460)

/* A map of items that can be filled with water. */
private val FILLABLES = Map(
  1923 -> 1921, // Bowl
  229 -> 227, // Vial
  1925 -> 1929, // Bucket
  1980 -> 4458, // Cup
  1935 -> 1937 // Jug
)


/* An Action that will fill all fillables of specific type with water. */
private final class FillAction(plr: Player, oldId: Int, newId: Int) extends ProducingSkillAction(plr, true, 1) {
  override def remove = Array(new Item(oldId))
  override def add = Array(new Item(newId))

  override def onProduce() = plr.animation(ANIMATION)
}


/* Intercept an item on object event, fill items if applicable. */
intercept[ItemOnObjectEvent] { (msg, plr) =>
  if (WATER_SOURCES.contains(msg.getObjectId)) {
    FILLABLES.get(msg.getItemId).foreach { it =>
      plr.submitAction(new FillAction(plr, msg.getItemId, it))
      msg.terminate
    }
  }
}