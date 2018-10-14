import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item


/* Added to inventory. */
private val STARTER_INVENTORY = List(
  new Item(995, 10000), // Coins
  new Item(556, 250), // Air runes
  new Item(555, 250), // Water runes
  new Item(554, 250), // Fire runes
  new Item(557, 250), // Earth runes
  new Item(558, 500), // Mind runes
  new Item(841) // Shortbow
)

/* Added to equipment. */
private val STARTER_EQUIPMENT = List(
  new Item(1153), // Iron full helm
  new Item(1115), // Iron platebody
  new Item(1067), // Iron platelegs
  new Item(1323), // Iron scimitar
  new Item(1191), // Iron kiteshield
  new Item(1731), // Amulet of power
  new Item(4121), // Iron boots
  new Item(1063), // Leather vambraces
  new Item(2570), // Ring of life
  new Item(1019), // Black cape
  new Item(882, 750) // Bronze arrows
)


/* Give 'starter package' if the player is new. */
on[LoginEvent] { msg =>
  val plr = msg.plr
  if (plr.attr("first_login")) {
    plr.sendMessage("This is your first login. Enjoy your starter package!")

    plr.inventory.addAll(STARTER_INVENTORY)
    plr.equipment.addAll(STARTER_EQUIPMENT)

    plr.attr("first_login", false)
  }
}

