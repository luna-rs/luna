/*
 Herblore plugin, supports:
  -> Cleaning grimy herbs
  -> Making unfinished potions
  -> Making potions from unfinished potions and a secondary ingredient
*/

import io.luna.game.action.ProducingSkillAction
import io.luna.game.event.impl.{ItemFirstClickEvent, ItemOnItemEvent}
import io.luna.game.model.`def`.ItemDefinition.computeNameForId
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.{Animation, Player, Skill}


/* Class representing herbs in the 'HERB_TABLE'. */
private case class Herb(grimyId: Int, cleanId: Int, unfId: Int, unfLevel: Int, cleanLevel: Int, cleanExp: Double)

/* Class representing potions in the 'POTION_TABLE'. */
private case class Potion(id: Int, level: Int, unfId: Int, secondaryId: Int, exp: Double)


/* Item identifier for a vial of water. */
private val VIAL_OF_WATER = 227

/* Animation for making both unfinished and finished potions. */
private val POTION_ANIMATION = new Animation(363)

/*
 A table of all the herbs that can be cleaned and made into unfinished potions.

 herb_symbol -> Herb
*/
private val HERB_TABLE = Map(
  'guam_leaf -> Herb(199, 249, 91, 3, 3, 2.5),
  'marrentill -> Herb(201, 251, 93, 5, 5, 3.8),
  'tarromin -> Herb(203, 253, 95, 12, 11, 5.0),
  'harralander -> Herb(205, 255, 97, 22, 20, 6.3),
  'ranarr_weed -> Herb(207, 257, 25, 30, 99, 7.5),
  'toadflax -> Herb(3049, 2998, 3002, 34, 30, 8.0),
  'irit_leaf -> Herb(209, 259, 101, 45, 40, 8.8),
  'avantoe -> Herb(211, 261, 103, 50, 48, 10.0),
  'kwuarm -> Herb(213, 263, 105, 55, 54, 11.3),
  'snapdragon -> Herb(3051, 3000, 3004, 63, 59, 11.8),
  'cadantine -> Herb(215, 265, 107, 66, 66, 65, 12.5),
  'lantadyme -> Herb(2485, 2481, 2483, 69, 67, 13.1),
  'dwarf_weed -> Herb(217, 267, 109, 72, 70, 13.8),
  'torstol -> Herb(219, 269, 75, 78, 111, 15.0)
)

/*
 A different mapping of the 'HERB_TABLE' that maps grimy herb identifiers to their data.

 grimy_id -> Herb
*/
private val GRIMY_TO_HERB = HERB_TABLE.values.map { it => it.grimyId -> it }.toMap

/*
 A different mapping of the 'HERB_TABLE' that maps clean herb identifiers to their data.

 clean_id -> Herb
*/
private val CLEAN_TO_HERB = HERB_TABLE.values.map { it => it.cleanId -> it }.toMap

/*
 A table of all the potions that can be made from unfinished potions and a secondary ingredient.

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
  'defence_potion -> Potion(133, 30, 25, 239, 75.0),
  'agility_potion -> Potion(3034, 34, 3002, 2152, 80.0),
  'prayer_potion -> Potion(139, 38, 25, 231, 87.5),
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

 (secondary_id, unf_potion_id) -> Potion
*/
private val INGREDIENTS_TO_POTION = POTION_TABLE.values.map { it => (it.secondaryId, it.unfId) -> it }.toMap


/*
 A 'ProducingSkillAction' that will produce unfinished or finished potions from two ingredients. It will
 instantly make them at a rate of one per 2 ticks.
*/
private abstract class MakePotionAction(plr: Player, level: Int) extends ProducingSkillAction(plr, true, 2) {

  override def canInit = isLevel(plr, level)
  override def onProduce() = plr.sendMessage(onProduceMessage)

  def onProduceMessage: String
}


/* Determine if the required level is met by the player. */
private def isLevel(plr: Player, level: Int) = {
  val levelRequired = herb.level

  if (plr.skill(Skill.HERBLORE).getLevel < levelRequired) {
    plr.sendMessage(s"You need a Herblore level of $levelRequired to do this.")
    false
  } else {
    true
  }
}

/* Attempt to clean the grimy herb. */
private def cleanHerb(plr: Player, level: Int) {
  if (isLevel(plr, level)) {
    plr.interruptAction()

    plr.inventory.remove(new Item(herb.grimyId))
    plr.inventory.add(new Item(herb.cleanId))

    plr.skill(Skill.HERBLORE).addExperience(herb.experience)

    plr.sendMessage(s"You clean the ${computeNameForId(herb.cleanId)}.")
  }
}

/* Submits an action that will make the player produce unfinished potions. */
private def makeUnfPotion(plr: Player, herb: Herb) {

  plr.submitAction(new MakePotionAction(plr, herb.unfLevel) {
    override def newItems = Array(new Item(herb.unfId))
    override def oldItems = Array(new Item(herb.cleanId), new Item(VIAL_OF_WATER))
    override def onProduceMessage = s"You put the ${computeNameForId(herb.cleanId)} into the vial of water."
  })
}

/* Submits an action that will make the player produce finished potions. */
private def makeFinishedPotion(plr: Player, potion: Potion) = {

  plr.submitAction(new MakePotionAction(plr, potion.level) {
    override def newItems = Array(new Item(potion.id))
    override def oldItems = Array(new Item(potion.unfId), new Item(potion.secondaryId))
    override def onProduceMessage = s"You mix the ${computeNameForId(potion.secondaryId)} into your potion."
  })
}


/* If the item clicked is a grimy herb, clean it. */
>>[ItemFirstClickEvent] { (msg, plr) =>
  GRIMY_TO_HERB.get(msg.getId).foreach { it =>
    cleanHerb(plr, it)
    msg.terminate
  }
}

/* If a clean herb is used on a vial of water, make an unfinished potion. */
>>[ItemOnItemEvent] { (msg, plr) =>
  if (msg.getTargetId == VIAL_OF_WATER) {
    CLEAN_TO_HERB.get(msg.getUsedId).foreach { it =>
      makeUnfPotion(plr, it)
      msg.terminate
    }
  }
}

/* If a secondary ingredient is used on an unfinished potion, make a finished potion. */
>>[ItemOnItemEvent] { (msg, plr) =>
  val usedId = msg.getUsedId
  val targetId = msg.getTargetId

  INGREDIENTS_TO_POTION.get((usedId, targetId)).foreach { it =>
    makeFinishedPotion(plr, it)
    msg.terminate
  }
}