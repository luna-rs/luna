/*
 A plugin that adds functionality for performing specific tasks on login.

 SUPPORTS:
  -> Giving 'starter packages' on first login.
  -> Indicating if the player is muted (and for how long).
  -> Configuring interface states.

 AUTHOR: lare96
*/

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item


/* Formats dates into the pattern specified. */
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, uuuu")

/* A table of items that will be added to the inventory on the first login. */
private val STARTER_ITEMS = List(
  new Item(995, 10000), // Coins
  new Item(556, 250), // Air runes
  new Item(555, 250), // Water runes
  new Item(554, 250), // Fire runes
  new Item(557, 250), // Earth runes
  new Item(558, 500), // Mind runes
  new Item(841) // Shortbow
)

/* A table of equipment that will be equipped dynamically on the first login. */
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

    plr.inventory.addAll(STARTER_ITEMS)
    plr.equipment.addAll(STARTER_EQUIPMENT)

    plr.attr("first_login", false)
  }
}

/* If the player is muted, indicate that they are and when it will be lifted. */
on[LoginEvent] { msg =>
  val plr = msg.plr
  val date: String = plr.attr("unmute_date")

  date match {
    case "n/a" => // Do nothing, we aren't muted.
    case "never" => plr.sendMessage("You are permanently muted. It can only be overturned by an administrator.")
    case _ => plr.sendMessage(s"You are muted. You will be unmuted on ${ DATE_FORMATTER.format(LocalDate.parse(date)) }.")
  }
}

/* Configure interface states. */
on[LoginEvent] { msg =>
  val plr = msg.plr

  plr.sendConfig(173, if (plr.walking.isRunning) 1 else 0)
}