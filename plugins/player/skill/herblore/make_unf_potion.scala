/*
 A plugin for the Herblore skill that adds functionality for making unfinished potions.

 SUPPORTS:
  -> Making unfinished potions from all herbs.

 AUTHOR: lare96
*/

import io.luna.game.action.ProducingAction
import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.{Animation, Player}


/* Class representing unfinished potions in the 'UNF_POTION_TABLE'. */
private case class UnfPotion(herb: Int, unf: Int, level: Int)


/* Item identifier for a vial of water. */
private val VIAL_OF_WATER = 227

/* Animation for making unfinished potions. */
private val ANIMATION = new Animation(363)

/*
 A table of all the unfinished potions.

 unf_potion_symbol -> UnfPotion
*/
private val UNF_POTION_TABLE = Map(
  'unf_guam -> UnfPotion(herb = 249,
    unf = 91,
    level = 3),

  'unf_marrentill -> UnfPotion(herb = 251,
    unf = 93,
    level = 5),

  'unf_tarromin -> UnfPotion(herb = 253,
    unf = 95,
    level = 8),

  'unf_harralander -> UnfPotion(herb = 255,
    unf = 97,
    level = 15),

  'unf_ranarr -> UnfPotion(herb = 257,
    unf = 99,
    level = 30),

  'unf_toadflax -> UnfPotion(herb = 2998,
    unf = 3002,
    level = 34),

  'unf_irit -> UnfPotion(herb = 259,
    unf = 101,
    level = 45),

  'unf_avantoe -> UnfPotion(herb = 261,
    unf = 103,
    level = 50),

  'unf_kwuarm -> UnfPotion(herb = 263,
    unf = 105,
    level = 55),

  'unf_snapdragon -> UnfPotion(herb = 3000,
    unf = 3004,
    level = 63),

  'unf_cadantine -> UnfPotion(herb = 265,
    unf = 107,
    level = 66),

  'unf_lantadyme -> UnfPotion(herb = 2481,
    unf = 2483,
    level = 69),

  'unf_dwarf_weed -> UnfPotion(herb = 267,
    unf = 109,
    level = 72),

  'unf_torstol -> UnfPotion(herb = 269,
    unf = 111,
    level = 78)
)

/*
 A different mapping of the 'UNF_POTION_TABLE' that maps identified herbs to their data.

 identified_id -> UnfPotion
*/
private val IDENTIFIED_TO_UNF = UNF_POTION_TABLE.values.map(unf => unf.herb -> unf).toMap


/* An Action that will be used to make unfinished potions. */
private final class MakeUnfAction(plr: Player, unf: UnfPotion) extends ProducingAction(plr, true, 2) {

  override def canInit = {
    val levelRequired = unf.level
    if (plr.skill(SKILL_HERBLORE).getLevel < levelRequired) {
      plr.sendMessage(s"You need a Herblore level of $levelRequired to make this potion.")
      false
    } else {
      true
    }
  }

  override def onProduce() = {
    plr.sendMessage(s"You put the ${ nameOfItem(unf.herb) } into the vial of water.")
    plr.animation(ANIMATION)
  }

  override def add = Array(new Item(unf.unf))
  override def remove = Array(new Item(unf.herb), new Item(VIAL_OF_WATER))
}


/* Perform a lookup for the identifier and start the MakeUnfAction if successful. */
private def makeUnf(plr: Player, msg: Event, herb: Int) = {
  IDENTIFIED_TO_UNF.get(herb).foreach { it =>
    plr.submitAction(new MakeUnfAction(plr, it))
    msg.terminate
  }
}


/* Make unfinished potions if the required items are present. */
on[ItemOnItemEvent] { msg =>
  if (msg.targetId == VIAL_OF_WATER) {
    makeUnf(msg.plr, msg, msg.usedId)
  } else if (msg.usedId == VIAL_OF_WATER) {
    makeUnf(msg.plr, msg, msg.targetId)
  }
}
