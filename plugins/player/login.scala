import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item

val STARTER_ITEMS = Vector(
  new Item(995, 10000),
  new Item(1323),
  new Item(556, 500),
  new Item(555, 500),
  new Item(554, 500),
  new Item(557, 500),
  new Item(558, 1000),
  new Item(841),
  new Item(882, 750)
)

>>[LoginEvent] { (msg, plr) =>
  val inventory = plr.getInventory

  if (plr.attr("first_login")) {
    plr.sendMessage("This is your first login. Enjoy your starter package!")
    inventory.addAll(STARTER_ITEMS)
    plr.attr("first_login", false)
  }
}

>>[LoginEvent] { (msg, plr) =>
  if (plr.attr("unmute_date") != "n/a") {
    plr.sendMessage("You are currently muted. Other players will not see the text you write.")
  }
}