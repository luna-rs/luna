/*
 A plugin for the Herblore skill that adds functionality for making unfinished potions.

 SUPPORTS:
  -> Making unfinished potions from all herbs.

 TODO:
  -> Verify if vial of water can be used on herb.

 AUTHOR: lare96
*/

import io.luna.game.action.ProducingSkillAction
import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Skill.HERBLORE
import io.luna.game.model.mobile.{Animation, Player}


/* Class representing unfinished potions in the 'UNF_POTION_TABLE'. */
private case class UnfPotion(herbId: Int, unfId: Int, level: Int)


/* Item identifier for a vial of water. */
private val VIAL_OF_WATER = 227

/* Animation for making unfinished potions. */
private val ANIMATION = new Animation(363)

/*
 A table of all the unfinished potions.

 unf_potion_symbol -> UnfPotion
*/
private val UNF_POTION_TABLE = Map(
  'unf_guam -> UnfPotion(249, 91, 3),
  'unf_marrentill -> UnfPotion(251, 93, 5),
  'unf_tarromin -> UnfPotion(253, 95, 8),
  'unf_harralander -> UnfPotion(255, 97, 15),
  'unf_ranarr -> UnfPotion(257, 99, 30),
  'unf_toadflax -> UnfPotion(2998, 3002, 34),
  'unf_irit -> UnfPotion(259, 101, 45),
  'unf_avantoe -> UnfPotion(261, 103, 50),
  'unf_kwuarm -> UnfPotion(263, 105, 55),
  'unf_snapdragon -> UnfPotion(3000, 3004, 63),
  'unf_cadantine -> UnfPotion(265, 107, 66),
  'unf_lantadyme -> UnfPotion(2481, 2483, 69),
  'unf_dwarf_weed -> UnfPotion(267, 109, 72),
  'unf_torstol -> UnfPotion(269, 111, 78)
)

/*
 A different mapping of the 'UNF_POTION_TABLE' that maps identified herbs to their data.

 identified_id -> UnfPotion
*/
private val IDENTIFIED_TO_UNF = UNF_POTION_TABLE.values.map(unf => unf.herbId -> unf).toMap


/* An Action that will be used to make unfinished potions. */
private final class MakeUnfAction(plr: Player, unf: UnfPotion) extends ProducingSkillAction(plr, true, 2) {

  override def canInit = {
    val levelRequired = unf.level
    if (plr.skill(HERBLORE).getLevel < levelRequired) {
      plr.sendMessage(s"You need a Herblore level of $levelRequired to make this potion.")
      false
    } else {
      true
    }
  }

  override def onProduce() = {
    plr.sendMessage(s"You put the ${computeItemName(unf.herbId)} into the vial of water.")
    plr.animation(ANIMATION)
  }

  override def add = Array(new Item(unf.unfId))
  override def remove = Array(new Item(unf.herbId), new Item(VIAL_OF_WATER))
}


/* Perform a lookup for the identifier and start the MakeUnfAction if successful. */
private def makeUnf(plr: Player, evt: Event, herbId: Int) = {
  IDENTIFIED_TO_UNF.get(herbId).foreach { it =>
    plr.submitAction(new MakeUnfAction(plr, it))
    evt.terminate
  }
}


/* Make unfinished potions if the required items are present. */
intercept[ItemOnItemEvent] { (msg, plr) =>
  if (msg.getTargetId == VIAL_OF_WATER) {
    makeUnf(plr, msg, msg.getUsedId)
  } else if (msg.getUsedId == VIAL_OF_WATER) {
    makeUnf(plr, msg, msg.getTargetId)
  }
}
