/*
 A plugin that adds functionality for drinking potions.

 SUPPORTS:
  -> Drinking a variety of potions.
  -> Potion status and level effects.

 TODO:
  -> Support for more potions.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.{Animation, Player}


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

/* The delay between consuming potions. */
private val CONSUME_DELAY = 1200 // TODO confirm if delay is really 1200ms

/*
 A table of all the potions that can be drank.

 potion_symbol -> Potion
*/
private val POTION_TABLE = Map(
  'zamorak_brew -> Potion(fourDose = 2450,
    threeDose = 189,
    twoDose = 191,
    oneDose = 193,
    effect = onZamorakBrew),

  'saradomin_brew -> Potion(fourDose = 6685,
    threeDose = 6687,
    twoDose = 6689,
    oneDose = 6691,
    effect = onSaradominBrew),

  'agility_potion -> Potion(fourDose = 3032,
    threeDose = 3034,
    twoDose = 3036,
    oneDose = 3038,
    effect = onSkillPotion(_, SKILL_AGILITY)),

  'fishing_potion -> Potion(fourDose = 2438,
    threeDose = 151,
    twoDose = 153,
    oneDose = 155,
    effect = onSkillPotion(_, SKILL_FISHING)),

  'ranging_potion -> Potion(fourDose = 2444,
    threeDose = 169,
    twoDose = 171,
    oneDose = 173,
    effect = onCombatPotion(_, SKILL_RANGED)),

  'magic_potion -> Potion(fourDose = 3040,
    threeDose = 3042,
    twoDose = 3044,
    oneDose = 3046,
    effect = onCombatPotion(_, SKILL_MAGIC)),

  'defence_potion -> Potion(fourDose = 2432,
    threeDose = 133,
    twoDose = 135,
    oneDose = 137,
    effect = onCombatPotion(_, SKILL_DEFENCE)),

  'strength_potion -> Potion(fourDose = 113,
    threeDose = 115,
    twoDose = 117,
    oneDose = 119,
    effect = onCombatPotion(_, SKILL_STRENGTH)),

  'attack_potion -> Potion(fourDose = 2428,
    threeDose = 121,
    twoDose = 123,
    oneDose = 125,
    effect = onCombatPotion(_, SKILL_ATTACK)),

  'super_defence -> Potion(fourDose = 2442,
    threeDose = 163,
    twoDose = 165,
    oneDose = 167,
    effect = onCombatPotion(_, SKILL_DEFENCE, true)),

  'super_attack -> Potion(fourDose = 2436,
    threeDose = 145,
    twoDose = 147,
    oneDose = 149,
    effect = onCombatPotion(_, SKILL_ATTACK, true)),

  'super_strength -> Potion(fourDose = 2440,
    threeDose = 157,
    twoDose = 159,
    oneDose = 161,
    effect = onCombatPotion(_, SKILL_STRENGTH, true)),

  'energy_potion -> Potion(fourDose = 3008,
    threeDose = 3010,
    twoDose = 3012,
    oneDose = 3014,
    effect = onEnergyPotion(_, false)),

  'super_energy -> Potion(fourDose = 3016,
    threeDose = 3018,
    twoDose = 3020,
    oneDose = 3022,
    effect = onEnergyPotion(_, true)),

  'antipoison_potion -> Potion(fourDose = 2446,
    threeDose = 175,
    twoDose = 177,
    oneDose = 179,
    effect = onAntipoison(_, None)),

  'super_antipoison -> Potion(fourDose = 2448,
    threeDose = 181,
    twoDose = 183,
    oneDose = 185,
    effect = onAntipoison(_, Some(500))),

  'antidote_+ -> Potion(fourDose = 5943,
    threeDose = 5945,
    twoDose = 5947,
    oneDose = 5949,
    effect = onAntipoison(_, Some(1000))),

  'antidote_++ -> Potion(fourDose = 5952,
    threeDose = 5954,
    twoDose = 5956,
    oneDose = 5958,
    effect = onAntipoison(_, Some(1200))),

  'prayer_potion -> Potion(fourDose = 2434,
    threeDose = 139,
    twoDose = 141,
    oneDose = 143,
    effect = onPrayerPotion),

  'anti_fire_potion -> Potion(fourDose = 2452,
    threeDose = 2454,
    twoDose = 2456,
    oneDose = 2458,
    effect = onAntifirePotion),

  'super_restore -> Potion(fourDose = 3024,
    threeDose = 3026,
    twoDose = 3028,
    oneDose = 3030,
    effect = onRestorePotion(_, true))
)

/*
 A different mapping of the 'POTION_TABLE' that maps potion doses to their data.

 potion_id -> Potion
*/
private val ID_TO_POTION = {
  val mappings = /* Creates an Iterable[List[(Int, Potion)]] */
    for {
      (symbol, potion) <- POTION_TABLE
    } yield potion.oneDose -> potion ::
      potion.twoDose -> potion ::
      potion.threeDose -> potion ::
      potion.fourDose -> potion :: Nil

  mappings.
    flatten. /* We flatten it to Iterable[(Int, Potion)] */
    toMap /* And finally, convert it to a Map[Int, Potion] */
}


/* A function invoked when a zamorak brew is sipped. */
private def onZamorakBrew(plr: Player) = {
  val attack = plr.skill(SKILL_ATTACK)
  val strength = plr.skill(SKILL_STRENGTH)
  val defence = plr.skill(SKILL_DEFENCE)
  val hp = plr.skill(SKILL_HITPOINTS)
  val prayer = plr.skill(SKILL_PRAYER)

  attack.increaseLevel(2 + (0.20 * attack.getStaticLevel).toInt)
  strength.increaseLevel(2 + (0.12 * strength.getStaticLevel).toInt)
  defence.decreaseLevel(2 + (0.10 * defence.getStaticLevel).toInt)
  hp.decreaseLevel(2 + (0.10 * hp.getStaticLevel).toInt, 0)
  prayer.increaseLevel((0.10 * prayer.getStaticLevel).toInt)
}

/* A function invoked when a saradomin brew is sipped. */
private def onSaradominBrew(plr: Player) = {
  val attack = plr.skill(SKILL_ATTACK)
  val strength = plr.skill(SKILL_STRENGTH)
  val defence = plr.skill(SKILL_DEFENCE)
  val hp = plr.skill(SKILL_HITPOINTS)
  val ranged = plr.skill(SKILL_RANGED)
  val magic = plr.skill(SKILL_MAGIC)

  defence.increaseLevel(2 + (0.20 * defence.getStaticLevel).toInt)
  hp.increaseLevel(2 + (0.15 * hp.getStaticLevel).toInt)
  attack.decreaseLevel((0.10 * attack.getStaticLevel).toInt, 0)
  strength.decreaseLevel((0.10 * strength.getStaticLevel).toInt, 0)
  magic.decreaseLevel((0.10 * magic.getStaticLevel).toInt, 0)
  ranged.decreaseLevel((0.10 * ranged.getStaticLevel).toInt, 0)
}

/* A function invoked when a potion with anti-poisoning properties is sipped. */
private def onAntipoison(plr: Player, immunityDuration: Option[Int]) = ???

/* A function invoked when a prayer potion is sipped. */
private def onPrayerPotion(plr: Player) = {
  val prayer = plr.skill(SKILL_PRAYER)
  prayer.increaseLevel(7 + (prayer.getStaticLevel / 4), prayer.getStaticLevel)
}

/* A function invoked when a non-combat skill potion is sipped. */
private def onSkillPotion(plr: Player, skillId: Int) = {
  val skill = plr.skill(skillId)
  skill.increaseLevel(3)
}

/* A function invoked when a energy or super energy potion is sipped. */
private def onEnergyPotion(plr: Player, superPotion: Boolean) = {
  val amount = if (superPotion) 0.20 else 0.10
  plr.setRunEnergy(plr.getRunEnergy + amount)
}

/* A function invoked when a restore or super restore potion is sipped. */
private def onRestorePotion(plr: Player, superPotion: Boolean) = {
  def boostAmount(level: Int) = if (superPotion) 8 + (0.25 * level) else 10 + (0.30 * level)

  plr.skills.lazyFilter(_.getId != SKILL_PRAYER). /* Perform normal restore operation. */
    lazyFilter(_.getId != SKILL_HITPOINTS).
    foreach(skill => skill.increaseLevel(boostAmount(skill.getStaticLevel).toInt, skill.getStaticLevel))

  if (superPotion) {
    /* If super restore is being sipped, restore prayer as well. */
    val prayer = plr.skill(SKILL_PRAYER)
    prayer.increaseLevel(8 + (prayer.getStaticLevel / 4), prayer.getStaticLevel)
  }
}

/* A function invoked when an anti-fire potion is sipped. */
private def onAntifirePotion(plr: Player) = ???

/* A function invoked when a combat skill potion is sipped. */
private def onCombatPotion(plr: Player, skillId: Int, superPotion: Boolean = false) = {
  def boostAmount(level: Int) = if (superPotion) 5 + (0.15 * level) else 3 + (0.10 * level)

  val skill = plr.skill(skillId)
  skill.increaseLevel(boostAmount(skill.getStaticLevel).toInt)
}

/* Attempts to drink a potion and apply the appropriate effects to the player. */
private def consume(plr: Player, potion: Potion, index: Int): Unit = {
  val inventory = plr.inventory
  val ids = Array(potion.fourDose, potion.threeDose, potion.twoDose, potion.oneDose)

  if (!plr.elapsedTime("last_potion_consume", CONSUME_DELAY)) {
    return
  }

  plr.interruptAction()

  val toConsume = inventory.get(index)
  if (inventory.remove(toConsume, index)) {

    val nextIndex = ids.indexOf(toConsume.getId) + 1
    if (ids.isDefinedAt(nextIndex)) { /* Add the next dose or an empty vial to the inventory. */
      inventory.add(new Item(ids(nextIndex)), index)
    } else {
      inventory.add(VIAL)
    }

    plr.sendMessage(s"You drink some of your ${ nameOfItem(toConsume.getId) }.")
    val dosesLeft = ids.length - nextIndex
    if (dosesLeft > 0) {
      plr.sendMessage(s"You have $dosesLeft doses of potion left.")
    } else {
      plr.sendMessage(s"You have finished your potion.")
    }

    plr.animation(ANIMATION)

    potion.effect(plr)
  }

  plr.resetTime("last_food_consume")
  plr.resetTime("last_potion_consume")
}


/* Intercept the item click event, and if the item is a potion then drink it. */
on[ItemFirstClickEvent] { msg =>
  ID_TO_POTION.get(msg.id).foreach { potion =>
    consume(msg.plr, potion, msg.index)
    msg.terminate
  }
}
