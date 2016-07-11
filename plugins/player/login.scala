import java.time.format.DateTimeFormatter

import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.{Equipment, Item}


val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, uuuu")

val STARTER_ITEMS = Vector(
  new Item(995, 10000), // Coins
  new Item(556, 250), // Air runes
  new Item(555, 250), // Water runes
  new Item(554, 250), // Fire runes
  new Item(557, 250), // Earth runes
  new Item(558, 500), // Mind runes
  new Item(841) // Shortbow
)

val STARTER_EQUIPMENT = Vector(
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


>>[LoginEvent] { (msg, plr) => // Give "starter package" if new player.
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