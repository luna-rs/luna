import io.luna.game.model.`def`.ItemDefinition._
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Skill._
import io.luna.game.model.mobile.{Animation, Player}


/* Class representing potions in the 'POTION_TABLE'. */
private case class Potion(
  fourDose: Int,
  threeDose: Int,
  twoDose: Int,
  oneDose: Int,
  effect: Player => Unit
)


/* Item instance for empty vials. */
private val VIAL = new Item(229)

/* Animation for drinking potions. */
private val ANIMATION = new Animation(829)

/*
 A table of all the potions that can be drank.

 potion_symbol -> Potion
*/
private val POTION_TABLE = Map(
  'zamorak_brew -> Potion(2450, 189, 191, 193, onZamorakBrew),
  'saradomin_brew -> Potion(6685, 6687, 6689, 6691, onSaradominBrew),
  'agility_potion -> Potion(3032, 3034, 3036, 3038, plr => onSkillPotion(plr, AGILITY)),
  'fishing_potion -> Potion(2438, 151, 153, 155, plr => onSkillPotion(plr, FISHING)),
  'ranging_potion -> Potion(2444, 169, 171, 173, plr => onCombatPotion(plr, RANGED)),
  'magic_potion -> Potion(3040, 3042, 3044, 3046, plr => onCombatPotion(plr, MAGIC)),
  'defence_potion -> Potion(2432, 133, 135, 137, plr => onCombatPotion(plr, DEFENCE)),
  'strength_potion -> Potion(113, 115, 117, 119, plr => onCombatPotion(plr, STRENGTH)),
  'attack_potion -> Potion(2428, 121, 123, 125, plr => onCombatPotion(plr, ATTACK)),
  'super_defence -> Potion(2442, 163, 165, 167, plr => onCombatPotion(plr, DEFENCE, true)),
  'super_attack -> Potion(2436, 145, 147, 149, plr => onCombatPotion(plr, ATTACK, true)),
  'super_strength -> Potion(2440, 157, 159, 161, plr => onCombatPotion(plr, STRENGTH, true)),
  'energy_potion -> Potion(3008, 3010, 3012, 3014, onEnergyPotion),
  'super_energy -> Potion(3016, 3018, 3020, 3022, plr => onEnergyPotion(plr, true)),
  'antipoison_potion -> Potion(2446, 175, 177, 179, onAntipoison),
  'super_antipoison -> Potion(2448, 181, 183, 185, plr => onAntipoison(plr, true, 500)),
  'antidote_+ -> Potion(5943, 5945, 5947, 5949, plr => onAntipoison(plr, true, 1000)),
  'antidote_++ -> Potion(5952, 5954, 5956, 5958, plr => onAntipoison(plr, true, 1200)),
  'prayer_potion -> Potion(2434, 139, 141, 143, onPrayerPotion),
  'anti_fire_potion -> Potion(2452, 2454, 2456, 2458, onAntifirePotion),
  'super_restore -> Potion(3024, 3026, 3028, 3030, plr => onRestorePotion(plr, true))
)


private def onZamorakBrew(plr: Player) = {
  val attack = plr.skill(ATTACK)
  val strength = plr.skill(STRENGTH)
  val defence = plr.skill(DEFENCE)
  val hp = plr.skill(HITPOINTS)
  val prayer = plr.skill(PRAYER)

  attack.increaseLevel(2 + (0.20 * attack.getStaticLevel))
  strength.increaseLevel(2 + (0.12 * strength.getStaticLevel))
  defence.decreaseLevel(2 + (0.10 * defence.getStaticLevel))
  hp.decreaseLevel(2 + (0.10 * hp.getStaticLevel, 0))
  prayer.increaseLevel(0.10 * prayer.getStaticLevel)
}

private def onSaradominBrew(plr: Player) = {
  val attack = plr.skill(ATTACK)
  val strength = plr.skill(STRENGTH)
  val defence = plr.skill(DEFENCE)
  val hp = plr.skill(HITPOINTS)
  val ranged = plr.skill(RANGED)
  val magic = plr.skill(MAGIC)

  defence.increaseLevel(2 + (0.20 * defence.getStaticLevel))
  hp.increaseLevel(2 + (0.15 * hp.getStaticLevel))
  attack.decreaseLevel(0.10 * attack.getStaticLevel, 0)
  strength.decreaseLevel(0.10 * strength.getStaticLevel, 0)
  magic.decreaseLevel(0.10 * magic.getStaticLevel, 0)
  ranged.decreaseLevel(0.10 * ranged.getStaticLevel, 0)
}

private def onAntipoison(plr: Player, immunity: Boolean = false, immunityDuration: Int = 0) = {
  // TODO Finish when combat is done.
}

private def onPrayerPotion(plr: Player) = {
  val prayer = plr.skill(PRAYER)
  prayer.increaseLevel(7 + (prayer.getStaticLevel / 4), prayer.getStaticLevel)
}

private def onSkillPotion(plr: Player, skillId: Int) = {
  val skill = plr.skill(skillId)
  skill.increaseLevel(3)
}

private def onEnergyPotion(plr: Player, superPotion: Boolean = false) = {
  val amount = if (superPotion) 0.20 else 0.10
  plr.setRunEnergy(plr.getRunEnergy + amount)
}

private def onRestorePotion(plr: Player, superPotion: Boolean = false) = {
  def boostAmount(level: Int) = if (superPotion) 8 + (0.25 * level) else 10 + (0.30 * level)

  allSkills.lazyFilter(_ != PRAYER).
    lazyFilter(_ != HITPOINTS).
    foreach { id =>
      val skill = plr.skill(id)
      skill.increaseLevel(boostAmount(skill.getStaticLevel), skill.getStaticLevel)
    }

  if (superPotion) {
    val prayer = plr.skill(PRAYER)
    prayer.increaseLevel(8 + (prayer.getStaticLevel / 4), prayer.getStaticLevel)
  }
}

private def onAntifirePotion(plr: Player) = {
  // TODO Finish when combat is done.
}

private def onCombatPotion(plr: Player, skillId: Int, superPotion: Boolean = false) = {
  def boostAmount(level: Int) = if (superPotion) 5 + (0.15 * level) else 3 + (0.10 * level)

  val skill = plr.skill(skillId)
  skill.increaseLevel(boostAmount(skill.getStaticLevel))
}

private def consume(plr: Player, potion: Potion, index: Int): Unit = {
  val inventory = plr.inventory
  val ids = Array(potion.fourDose, potion.threeDose, potion.twoDose, potion.oneDose)

  if (!plr.elapsedTime("last_potion_consume", food.consumeDelay)) {
    return
  }

  val toConsume = inventory.get(index)
  if (inventory.remove(toConsume, index)) {

    val nextIndex = ids.indexOf(toConsume.getId) + 1
    if (ids.isDefinedAt(nextIndex)) {
      inventory.add(new Item(ids(nextIndex)), index)
    } else {
      inventory.add(VIAL)
    }

    plr.sendMessage(s"You drink some of your ${getNameForId(toConsume.getId)}.")
    val dosesLeft = ids.length - nextIndex
    if (dosesLeft > 0) {
      plr.sendMessage(s"You have $dosesLeft doses of potion left.")
    } else {
      plr.sendMessage(s"You have finished your potion.")
    }

    plr.animation(ANIMATION)

    potion.effect(plr)
  }

  plr.resetTime("last_potion_consume")
}