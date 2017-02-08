/*
 A plugin related to the Woodcutting skill that adds functionality for searching bird's nests.

 SUPPORTS:
  -> Searching all bird nest types.
  -> Selection of random items for ring and seed bird nests.

 TODO:
  -> Rarity of random selection for 'SEEDS' and 'RINGS'.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Player


/* An empty nest item instance. */
private val EMPTY_NEST = new Item(5075)

/* A Seq of identifiers for seeds that can be obtained from nests. */
private val SEEDS = Vector(5312, 5283, 5284, 5285, 5286, 5313, 5314, 5288, 5287, 5315, 5289, 5316, 5290, 5317).map(new Item(_))

/* A Seq of identifiers for rings that can be obtained from nests. */
private val RINGS = Vector(1635, 1637, 1639, 1641, 1643).map(new Item(_))

/* A red egg item instance. */
private val RED_EGG = new Item(5076)

/* A blue egg item instance. */
private val BLUE_EGG = new Item(5077)

/* A green egg item instance. */
private val GREEN_EGG = new Item(5078)

/*
 A Map with mappings of nest identifiers to { () => Item } functions. We use functions so that 'rand(???)' will generate
 a new element on each invocation.
*/
private val NESTS = Map(
  5070 -> { () => RED_EGG }, // Red egg
  5071 -> { () => GREEN_EGG }, // Green egg
  5072 -> { () => BLUE_EGG }, // Blue egg
  5073 -> { () => rand(SEEDS) }, // Generate random seed
  5074 -> { () => rand(RINGS) } // Generate random ring
)


/* A function that allows the player to search a nest. */
private def searchNest(plr: Player, nestId: Int, nestFunc: () => Item) = {
  val inventory = plr.inventory

  if (inventory.computeRemainingSize >= 1) {
    val nestItem = nestFunc()

    inventory.remove(new Item(nestId))
    inventory.add(EMPTY_NEST)
    inventory.add(nestItem)

    plr.sendMessage(joinArticle("You take", nameOfItem(nestItem.getId), "out of the bird's nest."))
  } else {
    plr.sendMessage("You do not have enough space in your inventory.")
  }
}


/* Intercept an event that will call 'searchNest' if a nest was clicked. */
on[ItemFirstClickEvent] { msg =>
  val nestId = msg.id
  NESTS.get(nestId).foreach { item =>
    searchNest(msg.plr, nestId, item)
    msg.terminate
  }
}