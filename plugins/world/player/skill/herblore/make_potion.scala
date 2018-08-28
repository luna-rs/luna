/*
 Adds functionality for making unfinished potions.

 SUPPORTS:
  -> Making unfinished potions from all herbs.
*/

import io.luna.game.action.{Action, ProducingAction}
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.{Animation, Mob, Player}

// TODO Use dialogues once completed
/* Class representing potions in the 'POTION_TABLE'. */
private case class Potion(id: Int, unf: Int, secondary: Int, level: Int, exp: Double)


/* Animation for making finished potions. */
private val ANIMATION = new Animation(363)

/*
 A table of all the finished potions that can be made.

 potion_symbol -> Potion
*/
private val POTION_TABLE = Map(
  'attack_potion -> Potion(id = 221,
    unf = 91,
    secondary = 121,
    level = 3,
    exp = 25.0),

  'antipoison -> Potion(id = 175,
    unf = 91,
    secondary = 235,
    level = 5,
    exp = 37.5),

  'strength_potion -> Potion(id = 115,
    unf = 95,
    secondary = 225,
    level = 8,
    exp = 50.0),

  'serum_207 -> Potion(id = 3410,
    unf = 97,
    secondary = 592,
    level = 15,
    exp = 50.0),

  'restore_potion -> Potion(id = 127,
    unf = 97,
    secondary = 223,
    level = 22,
    exp = 62.5),

  'blamish_oil -> Potion(id = 1582,
    unf = 97,
    secondary = 1581,
    level = 25,
    exp = 80.0),

  'energy_potion -> Potion(id = 3010,
    unf = 97,
    secondary = 1975,
    level = 26,
    exp = 67.5),

  'defence_potion -> Potion(id = 133,
    unf = 99,
    secondary = 239,
    level = 30,
    exp = 75.0),

  'agility_potion -> Potion(id = 3034,
    unf = 3002,
    secondary = 2152,
    level = 34,
    exp = 80.0),

  'prayer_potion -> Potion(id = 139,
    unf = 99,
    secondary = 231,
    level = 38,
    exp = 87.5),

  'super_attack -> Potion(id = 145,
    unf = 101,
    secondary = 221,
    level = 45,
    exp = 100.0),

  'super_antipoison -> Potion(id = 181,
    unf = 101,
    secondary = 235,
    level = 48,
    exp = 106.3),

  'fishing_potion -> Potion(id = 151,
    unf = 103,
    secondary = 231,
    level = 50,
    exp = 112.5),

  'super_energy -> Potion(id = 3018,
    unf = 103,
    secondary = 2970,
    level = 52,
    exp = 117.5),

  'super_strength -> Potion(id = 157,
    unf = 105,
    secondary = 225,
    level = 55,
    exp = 125.0),

  'super_restore -> Potion(id = 3026,
    unf = 3004,
    secondary = 223,
    level = 63,
    exp = 142.5),

  'super_defence -> Potion(id = 163,
    unf = 107,
    secondary = 239,
    level = 66,
    exp = 150.0),

  'antifire_potion -> Potion(id = 2454,
    unf = 2483,
    secondary = 243,
    level = 69,
    exp = 157.5),

  'ranging_potion -> Potion(id = 169,
    unf = 109,
    secondary = 245,
    level = 72,
    exp = 162.5),

  'magic_potion -> Potion(id = 3042,
    unf = 2483,
    secondary = 3138,
    level = 76,
    exp = 172.5),

  'zamorak_brew -> Potion(id = 169,
    unf = 75,
    secondary = 247,
    level = 78,
    exp = 175.0),

  'saradomin_brew -> Potion(id = 169,
    unf = 3002,
    secondary = 6693,
    level = 81,
    exp = 180.0)
)

/*
 A different mapping of the 'POTION_TABLE' that maps ingredients to potions.

 (unf_potion_id, secondary_ingredient_id) -> Potion
*/
private val INGREDIENTS_TO_POTION =
  POTION_TABLE.values.map(potion => (potion.unf, potion.secondary) -> potion).toMap


/* An Action that will be used to make finished potions. */
private final class MakePotionAction(plr: Player, potion: Potion) extends ProducingAction(plr, true, 2) {

  private val skill = plr.skill(SKILL_HERBLORE)

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
    plr.sendMessage(s"You mix the ${ nameOfItem(potion.secondary) } into your potion.")
    plr.animation(ANIMATION)
    skill.addExperience(potion.exp)
  }

  override def add = Array(new Item(potion.id))
  override def remove = Array(new Item(potion.unf), new Item(potion.secondary))

  override def isEqual(other: Action[_]) = {
    other match {
      case action: MakePotionAction =>
        potion.id == action.potion.id &&
        potion.secondary == action.potion.secondary
      case _ => false
    }
  }
}


/* Make finished potions if the required items are present. */
on[ItemOnItemEvent] { msg =>
  val potionOption = INGREDIENTS_TO_POTION.get(msg.targetId -> msg.usedId).
    orElse(INGREDIENTS_TO_POTION.get(msg.usedId -> msg.targetId))

  potionOption.foreach { potion => /* Perform action for either the first or second lookup, or none. */
    msg.plr.submitAction(new MakePotionAction(msg.plr, potion))
    msg.terminate
  }
}
