/*
 A plugin for the Herblore skill that adds functionality for making unfinished potions.

 SUPPORTS:
  -> Making unfinished potions from all herbs.

 TODO:
  -> Verify if vial of water can be used on herb.

 AUTHOR: lare96
*/

import io.luna.game.action.ProducingSkillAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Skill.HERBLORE
import io.luna.game.model.mobile.{Animation, Player}


/* Class representing potions in the 'POTION_TABLE'. */
private case class Potion(id: Int, level: Int, unfId: Int, secondaryId: Int, exp: Double)


/* Animation for making finished potions. */
private val ANIMATION = new Animation(363)

/*
 A table of all the finished potions that can be made.

 potion_symbol -> Potion
*/
private val POTION_TABLE = Map(
  'attack_potion -> Potion(221, 3, 91, 121, 25.0),
  'antipoison -> Potion(175, 5, 91, 235, 37.5),
  'strength_potion -> Potion(115, 8, 95, 225, 50.0),
  'serum_207 -> Potion(3410, 15, 97, 592, 50.0),
  'restore_potion -> Potion(127, 22, 97, 223, 62.5),
  'blamish_oil -> Potion(1582, 25, 97, 1581, 80.0),
  'energy_potion -> Potion(3010, 26, 97, 1975, 67.5),
  'defence_potion -> Potion(133, 30, 99, 239, 75.0),
  'agility_potion -> Potion(3034, 34, 3002, 2152, 80.0),
  'prayer_potion -> Potion(139, 38, 99, 231, 87.5),
  'super_attack -> Potion(145, 45, 101, 221, 100.0),
  'super_antipoison -> Potion(181, 48, 101, 235, 106.3),
  'fishing_potion -> Potion(151, 50, 103, 231, 112.5),
  'super_energy -> Potion(3018, 52, 103, 2970, 117.5),
  'super_strength -> Potion(157, 55, 105, 225, 125.0),
  'super_restore -> Potion(3026, 63, 3004, 223, 142.5),
  'super_defence -> Potion(163, 66, 107, 239, 150.0),
  'antifire_potion -> Potion(2454, 69, 2483, 243, 157.5),
  'ranging_potion -> Potion(169, 72, 109, 245, 162.5),
  'magic_potion -> Potion(3042, 76, 2483, 3138, 172.5),
  'zamorak_brew -> Potion(169, 78, 75, 247, 175.0),
  'saradomin_brew -> Potion(169, 81, 3002, 6693, 180.0)
)

/*
 A different mapping of the 'POTION_TABLE' that maps ingredients to potions.

 (unf_potion_id, secondary_id) -> Potion
*/
private val INGREDIENTS_TO_POTION =
  POTION_TABLE.values.map(potion => (potion.unfId, potion.secondaryId) -> potion).toMap


/* An Action that will be used to make finished potions. */
private final class MakePotionAction(plr: Player, potion: Potion) extends ProducingSkillAction(plr, true, 2) {

  private val skill = plr.skill(HERBLORE)

  override def canInit = {
    val levelRequired = potion.level
    if (skill.getLevel < levelRequired) {
      plr.sendMessage(s"You need a Herblore level of $levelRequired to make this potion.")
      false
    } else {
      true
    }
  }

  override def onProduce() = {
    plr.sendMessage(s"You mix the ${computeItemName(potion.secondaryId)} into your potion.")
    plr.animation(ANIMATION)
    skill.addExperience(potion.exp)
  }

  override def add = Array(new Item(potion.id))
  override def remove = Array(new Item(potion.unfId), new Item(potion.secondaryId))
}


/* Make finished potions if the required items are present. */
intercept[ItemOnItemEvent] { (msg, plr) =>
  val usedId = msg.getUsedId
  val targetId = msg.getTargetId

  /* Perform a lookup both ways: if the first lookup isn't found it defaults to the second. */
  val potion = INGREDIENTS_TO_POTION.get(targetId -> usedId).
    orElse(INGREDIENTS_TO_POTION.get(usedId -> targetId))

  potion.foreach { it => /* Perform action for either the first or second lookup, or none. */
    plr.submitAction(new MakePotionAction(plr, it))
    msg.terminate
  }
}
