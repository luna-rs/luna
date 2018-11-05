import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.model.item.shop.ShopInterface

(`let new shop`
  `with name` "General Store"
  `and buy policy` BUY_ALL
  `and restock policy` RESTOCK_FAST
  selling "Pot" x 10
  selling "Jug" x 15
  selling "Tinderbox" x 15
  selling "Chisel" x 15
  selling "Hammer" x 15
  selling "Newcomer map" x 15
  selling "Bucket" x 15
  selling "Bowl" x 15
  selling "Anti-dragon shield" x 50
  selling "Lobster" x 150) register

/* Open the general store. */
onargs[NpcFirstClickEvent](520) {
  _.plr.interfaces.open(new ShopInterface("General Store"))
}