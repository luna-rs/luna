import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.model.item.shop.{RestockPolicy, SellPolicy, ShopInterface}

(shopBuilder
  `shop name` "General Store"
  `sell policy` SellPolicy.ALL
  `restock policy` RestockPolicy.FAST
  sell "Pot" x 10
  sell "Jug" x 15
  sell "Tinderbox" x 15
  sell "Chisel" x 15
  sell "Hammer" x 15
  sell "Newcomer map" x 15
  sell "Bucket" x 15
  sell "Bowl" x 15
  sell "Anti-dragon shield" x 50
  sell "Lobster" x 150) `add shop`

/* Open the general store. */
onargs[NpcFirstClickEvent](520) { _.plr.interfaces.open(new ShopInterface("General Store")) }