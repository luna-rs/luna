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

>>[LoginEvent] { (msg, plr) => // Give "starter package" if new player.
  val inventory = plr.getInventory

  if (plr.attr("first_login")) {
    plr.sendMessage("This is your first login. Enjoy your starter package!")
    inventory.addAll(STARTER_ITEMS)
    plr.attr("first_login", false)
  }
}

>>[LoginEvent] { (msg, plr) => // Send mute notification if muted.
  val date: String = plr.attr("unmute_date")

  date match {
    case "n/a" => // Do nothing, we aren't muted.
    case "never" => plr.sendMessage("You are permanently muted. It can only be overturned by an administrator.")
    case _ => plr.sendMessage(s"You are muted. You will be unmuted on ${DATE_FORMATTER.formatDate(date)}.")
  }
}

>>[LoginEvent] { (msg, plr) => // Configure states.
  plr.sendState(173, if (plr.getWalkingQueue.isRunning) 1 else 0)
}