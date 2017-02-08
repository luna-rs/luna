/*
 A plugin related to the Runecrafting skill that adds functionality for sending client configs when
 wearing tiaras.

 SUPPORTS:
  -> Sending all the correct tiara config values.
  -> Only sends the '0' config value on login or when a tiara is unequipped.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.{EquipmentChangeEvent, LoginEvent}
import io.luna.game.model.item.Equipment.HEAD
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Player


/* The tiara configuration key identifier. */
private val CONFIG_KEY = 491

/* A map of tiara identifiers to their configuration values. */
private val TIARAS = Map(
  5527 -> 1, // Air tiara
  5529 -> 2, // Mind tiara
  5531 -> 4, // Water tiara
  5535 -> 8, // Body tiara
  5537 -> 16, // Earth tiara
  5533 -> 31, // Fire tiara
  5539 -> 64, // Cosmic tiara
  5543 -> 128, // Nature tiara
  5541 -> 256, // Chaos tiara
  5545 -> 512, // Law tiara
  5547 -> 1024 // Death tiara
)


/* A function that sends the tiara key and value config when logging in. */
private def sendLoginConfig(plr: Player) = {
  val headId = plr.inventory.getIdForIndex(HEAD)
  val value = TIARAS.get(headId)

  plr.sendConfig(CONFIG_KEY, value.getOrElse(0))
}


/* A function that sends the tiara key and value config when changing equipment. */
private def sendEquipmentConfig(plr: Player, oldItem: Option[Item], newItem: Option[Item]) = {
  val oldTiara = oldItem.flatMap(x => TIARAS.get(x.getId))
  val newTiara = newItem.flatMap(x => TIARAS.get(x.getId))

  if (oldTiara.isDefined && newTiara.isEmpty) { // Unequipped item is a tiara, equipped item is not.
    plr.sendConfig(CONFIG_KEY, 0)
  } else if (oldTiara.isEmpty && newTiara.isDefined) { // Unequipped item is not a tiara, equipped item is.
    plr.sendConfig(CONFIG_KEY, newTiara.get)
  } else if (oldTiara.isDefined && newTiara.isDefined) { // Both the equipped and unequipped items are tiaras.
    plr.sendConfig(CONFIG_KEY, newTiara.get)
  }
}


/* Intercept event to send config key and value on login. */
on[LoginEvent] { msg => sendLoginConfig(msg.plr) }

/* Intercept event to send config key and value on equipment change. */
on[EquipmentChangeEvent] { msg =>
  if (msg.index == HEAD) {
    sendEquipmentConfig(msg.plr, msg.oldItem, msg.newItem)
  }
}