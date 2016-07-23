/*
 Runecrafting plugin, supports:
  -> Differentiating between rune essence and pure essence
  -> All runes on all altars
  -> Level multipliers (double cosmics, double nats, etc)

 TODO:
  -> Rune essence pouches
  -> Combination runes
  -> Tiaras
  -> Talismans
*/

import io.luna.game.event.impl.ObjectFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.{Animation, Graphic, Player, Skill}


/* Class representing runes in the 'RUNE_TABLE'. */
private case class Rune(runeId: Int, level: Int, exp: Double, multiplier: Int, altarId: Int, pureEssence: Boolean)


/* Rune essence identifier. */
private val RUNE_ESSENCE = 1436

/* Pure essence identifier. */
private val PURE_ESSENCE = 7936

/* Runecrafting animation. */
private val ANIMATION = new Animation(791)

/* Runecrafting graphic. */
private val GRAPHIC = new Graphic(186, 100)

/*
 A table of all the runes that can be crafted.

 rune_symbol -> Rune
*/
private val RUNE_TABLE = Map(
  'air_rune -> Rune(556, 1, 5.0, 11, 2478, false),
  'mind_rune -> Rune(558, 2, 5.5, 14, 2479, false),
  'water_rune -> Rune(555, 5, 6.0, 19, 2480, false),
  'earth_rune -> Rune(557, 9, 6.5, 26, 2481, false),
  'fire_rune -> Rune(554, 14, 7.0, 35, 2482, false),
  'body_rune -> Rune(559, 20, 7.5, 46, 2483, false),
  'cosmic_rune -> Rune(564, 27, 8.0, 59, 2484, true),
  'chaos_rune -> Rune(562, 35, 8.5, 74, 2487, true),
  'nature_rune -> Rune(561, 44, 9.0, 91, 2486, true),
  'law_rune -> Rune(563, 54, 9.5, 99, 2485, true),
  'death_rune -> Rune(560, 65, 10.0, 99, 2488, true),
  'blood_rune -> Rune(565, 80, 10.5, 99, 7141, true),
  'soul_rune -> Rune(566, 95, 11.0, 99, 7138, true)
)

/*
 A different mapping of the 'RUNE_TABLE' that maps altar object identifiers to 'Rune' data.

 altar_id -> Rune
*/
private val ALTAR_TO_RUNE = RUNE_TABLE.values.map { it => it.altarId -> it }.toMap


/* Attempt to craft the argued rune. */
private def craftRunes(plr: Player, rune: Rune): Unit = {
  val skill = plr.skill(Skill.RUNECRAFTING)

  val levelRequired = rune.level /* Are we a high enough level? */
  if (skill.getLevel < levelRequired) {
    plr.sendMessage(s"You need a Runecrafting level of $levelRequired to craft these runes.")
    return
  }

  /*
   Compute essence id and amount, which will result in one of two operations:
     1. If pure essence is required, do a lookup for it
     2. If pure essence isnt required, do a lookup for rune essence
       -> If rune essence isn't found, do a lookup for pure essence
  */
  val (essenceId, essenceCount) = {
    def lookup(id: Int) = plr.inventory.computeAmountForId(id)

    if (rune.pureEssence) {
      (PURE_ESSENCE, lookup(PURE_ESSENCE))
    } else {
      val count = lookup(RUNE_ESSENCE)
      if (count > 0) (RUNE_ESSENCE, count) else (PURE_ESSENCE, lookup(PURE_ESSENCE))
    }
  }

  if (essenceCount <= 0) { /* Do we have essence? */
    plr.sendMessage(s"You need some proper essence in order to craft these runes.")
    return
  }

  /* Compute rune count and craft runes. */
  val runeCount = (essenceCount * (skill.getLevel / rune.multiplier)) + essenceCount

  plr.interruptAction()

  plr.inventory.remove(new Item(essenceId, essenceCount))
  plr.inventory.add(new Item(rune.runeId, runeCount))

  plr.animation(ANIMATION)
  plr.graphic(GRAPHIC)

  skill.addExperience(rune.exp * essenceCount)
}


/* If the object being clicked is an altar, attempt to craft runes of that altar. */
>>[ObjectFirstClickEvent] { (msg, plr) =>
  ALTAR_TO_RUNE.get(msg.getId).foreach { it =>
    craftRunes(plr, it)
    msg.terminate
  }
}