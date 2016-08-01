import io.luna.game.action.ProducingSkillAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.`def`.ItemDefinition.getNameForId
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.{Animation, Player}


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
private final class GrindAction(plr: Player, oldId: Int, newId: Int) extends ProducingSkillAction(plr, true, 2) {

  override def onProduce() = {
    val nextWord = if (newId == 6693) "a" else "some"
    plr.sendMessage(s"You grind the ${getNameForId(oldId)} into $nextWord ${getNameForId(newId)}.")

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
intercept[ItemOnItemEvent] { (msg, plr) =>
  if (msg.getUsedId == PESTLE_AND_MORTAR) {
    grind(plr, msg.getTargetId, msg)
  } else if (msg.getTargetId == PESTLE_AND_MORTAR) {
    grind(plr, msg.getUsedId, msg)
  }
}