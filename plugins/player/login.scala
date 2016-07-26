/*
 A plugin that adds functionality for performing specific tasks on login.

 SUPPORTS:
  -> Giving 'starter packages' on first login.
  -> Indicating if the player is muted (and for how long).
  -> Configuring interface states.

 TODO:
  -> Add more interface states.

 AUTHOR: lare96
*/

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.{Equipment, Item}


/* Formats dates into the pattern specified. */
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, uuuu")

/* A table of items that will be added to the inventory on the first login. */
private val STARTER_ITEMS = Vector(
  new Item(995, 10000), // Coins
  new Item(556, 250), // Air runes
  new Item(555, 250), // Water runes
  new Item(554, 250), // Fire runes
  new Item(557, 250), // Earth runes
  new Item(558, 500), // Mind runes
  new Item(841) // Shortbow
)

/* A table of equipment that will be equipped dynamically on the first login. */
private val STARTER_EQUIPMENT = Vector(
  (Equipment.HEAD, new Item(1153)), // Iron full helm
  (Equipment.CHEST, new Item(1115)), // Iron platebody
  (Equipment.LEGS, new Item(1067)), // Iron platelegs
  (Equipment.WEAPON, new Item(1323)), // Iron scimitar
  (Equipment.SHIELD, new Item(1191)), // Iron kiteshield
  (Equipment.AMULET, new Item(1731)), // Amulet of power
  (Equipment.FEET, new Item(4121)), // Iron boots
  (Equipment.HANDS, new Item(1063)), // Leather vambraces
  (Equipment.RING, new Item(2570)), // Ring of life
  (Equipment.CAPE, new Item(1019)), // Black cape
  (Equipment.AMMUNITION, new Item(882, 750)) // Bronze arrows
)


/* Formats the given date, using 'DATE_FORMATTER'. */
private def formatDate(date: String) = DATE_FORMATTER.format(LocalDate.parse(date))


/* Give 'starter package' if the player is new. */
intercept[LoginEvent] { (msg, plr) =>
  val inventory = plr.inventory
  val equipment = plr.equipment

  if (plr.attr("first_login")) {
    plr.sendMessage("This is your first login. Enjoy your starter package!")

    inventory.addAll(STARTER_ITEMS)
    equipment.bulkOperation {
      STARTER_EQUIPMENT.foreach(it => equipment.set(it._1, it._2))
    }

    plr.attr("first_login", false)
  }
}

/* If the player is muted, indicate that they are and when it will be lifted. */
intercept[LoginEvent] { (msg, plr) =>
  val date: String = plr.attr("unmute_date")

  date match {
    case "n/a" => // Do nothing, we aren't muted.
    case "never" => plr.sendMessage("You are permanently muted. It can only be overturned by an administrator.")
    case _ => plr.sendMessage(s"You are muted. You will be unmuted on ${formatDate(date)}.")
  }
}

/* Configure interface states. */
intercept[LoginEvent] { (msg, plr) =>
  plr.sendState(173, if (plr.getWalkingQueue.isRunning) 1 else 0)
}