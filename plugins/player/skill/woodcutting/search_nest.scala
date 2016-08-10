import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Player


private val EMPTY_NEST = new Item(5075)
private val SEEDS = Seq(5312, 5283, 5284, 5285, 5286, 5313, 5314, 5288, 5287, 5315, 5289, 5316, 5290, 5317).map(new Item(_))
private val RINGS = Seq(1635, 1637, 1639, 1641, 1643).map(new Item(_))
private val RED_EGG = new Item(5076)
private val BLUE_EGG = new Item(5077)
private val GREEN_EGG = new Item(5078)
private val NESTS = Map(
  5070 -> (() => RED_EGG), // Red egg
  5071 -> (() => GREEN_EGG), // Green egg
  5072 -> (() => BLUE_EGG), // Blue egg
  5073 -> (() => SEEDS.randomElement), // Seed
  5074 -> (() => RINGS.randomElement) // Ring
)


private def searchNest(plr: Player, nestId: Int, itemFunc: () => Item) = {
  val inventory = plr.inventory

  if (inventory.computeRemainingSize >= 1) {
    inventory.remove(new Item(nestId))
    inventory.add(EMPTY_NEST)
    inventory.add(itemFunc())

    plr.sendMessage("You remove the contents from the bird's nest.")
  } else {
    plr.sendMessage("You do not have enough space in your inventory.")
  }
}


intercept[ItemFirstClickEvent] { (msg, plr) =>
  val nestId = msg.getId
  NESTS.get(nestId).foreach { item =>
    searchNest(plr, nestId, item)
    msg.terminate
  }
}