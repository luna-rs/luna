import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerAppearance.DesignPlayerInterface
import io.luna.net.msg.out.{AssignmentMessageWriter, SkillUpdateMessageWriter, UpdateRunEnergyMessageWriter}


/* Formats dates into the specified pattern. */
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, uuuu")

/* Inventory starter items. */
private val STARTER_INVENTORY = Vector(
  new Item(995, 10000), // Coins
  new Item(556, 250), // Air runes
  new Item(555, 250), // Water runes
  new Item(554, 250), // Fire runes
  new Item(557, 250), // Earth runes
  new Item(558, 500), // Mind runes
  new Item(841) // Shortbow
)

/* Equipment starter items. */
private val STARTER_EQUIPMENT = Vector(
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


/* Called when the player logs in for the first time. */
private def firstLogin(plr: Player): Unit = {
  plr.sendMessage("This is your first login. Enjoy your starter package!")

  plr.inventory.addAll(STARTER_INVENTORY)
  plr.equipment.addAll(STARTER_EQUIPMENT)
  plr.interfaces.open(new DesignPlayerInterface)
}

/* Final initialization of the player before gameplay. */
private def init(plr: Player) = {
  plr.tabs.resetAll()

  plr.interactions.show(INTERACTION_FOLLOW)
  plr.interactions.show(INTERACTION_TRADE)

  // Temporary, until "init" for containers is moved here. That will happen
  // when login synchronization is being fixed.
  plr.equipment.loadBonuses()

  plr.inventory.refreshPrimary(plr)
  plr.equipment.refreshPrimary(plr)

  plr.queue(new UpdateRunEnergyMessageWriter)
  plr.queue(new AssignmentMessageWriter(true))

  plr.skills.forEach { skill =>
    plr.queue(new SkillUpdateMessageWriter(skill.getId))
  }

  plr.sendMessage("Welcome to Luna.")
  plr.sendMessage("You currently have " + plr.rights.getFormattedName + " privileges.")
}

/* If the player is muted, send the mute details. */
private def checkMute(plr: Player) = {
  val date: String = plr.attr("unmute_date")
  date match {
    case "n/a" => // Do nothing, we aren't muted.
    case "never" => plr.sendMessage("You are permanently muted. It can only be overturned by an Admin.")
    case _ =>
      val localDate = LocalDate.parse(date)
      plr.sendMessage(s"You are muted. You will be unmuted on ${ DATE_FORMATTER.format(localDate) }.")
  }
}


/* Called on login. */
on[LoginEvent].run { msg =>
  val plr = msg.plr
  init(plr)
  checkMute(plr)
  if (plr.attr("first_login")) {
    firstLogin(plr)
    plr.attr("first_login", false)
  }
}
