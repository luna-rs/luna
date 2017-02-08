import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.{Item, RationalItem, RationalItemTable}


private val CASKET = new Item(405)
private val DEFAULT = new Item(995, 500)
private val CASKET_TABLE = new RationalItemTable(
  new RationalItem(995, 8 to 3000, CHANCE_COMMON), // Coins
  new RationalItem(1623, 1, CHANCE_COMMON), // Uncut sapphire
  new RationalItem(1621, 1, CHANCE_UNCOMMON), // Uncut emerald
  new RationalItem(1619, 1, CHANCE_UNCOMMON), // Uncut ruby
  new RationalItem(1617, 1, CHANCE_RARE), // Uncut diamond
  new RationalItem(987, 1, CHANCE_RARE), // Loop-half of key
  new RationalItem(985, 1, CHANCE_RARE), // Tooth-half of key
  new RationalItem(1454, 1, CHANCE_COMMON), // Cosmic talisman
  new RationalItem(1452, 1, CHANCE_UNCOMMON), // Chaos talisman
  new RationalItem(1462, 1, CHANCE_RARE) // Nature talisman
)


onargs[ItemFirstClickEvent](CASKET.getId) { msg =>
  // TODO add item dialogue
  val inventory = msg.plr.inventory
  if (inventory.remove(CASKET)) {
    val item = CASKET_TABLE.selectIndexed.getOrElse(DEFAULT)
    inventory.add(item)
  }
}


