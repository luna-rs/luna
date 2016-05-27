import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item

// TODO: Give starter items once item container system is done

val STARTER_ITEMS = List(
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
  if (plr.attr("first_login")) {
    plr.sendMessage("This is your first login. Enjoy your starter package!")
    plr.attr("first_login", false)
  }
}

