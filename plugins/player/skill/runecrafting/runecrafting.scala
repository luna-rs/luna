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


/* Type alias for rune tuple. */
private type Rune = (Int, Int, Double, Int, Int, Boolean)


/* Rune essence identifier. */
private val RUNE_ESSENCE = 1436

/* Pure essence identifier. */
val PURE_ESSENCE = 7936

/* Runecrafting animation. */
private val ANIMATION = new Animation(791)

/* Runecrafting graphic. */
private val GRAPHIC = new Graphic(186, 100)

/*
 A table of all the runes that can be crafted.

 rune_symbol -> (level, id, experience, rune_multiplier, altar_object_id, pure_essence?)
*/
private val RUNES = Map(
  'air_rune -> (1, 556, 5.0, 11, 2478, false),
  'mind_rune -> (2, 558, 5.5, 14, 2479, false),
  'water_rune -> (5, 555, 6.0, 19, 2480, false),
  'earth_rune -> (9, 557, 6.5, 26, 2481, false),
  'fire_rune -> (14, 554, 7.0, 35, 2482, false),
  'body_rune -> (20, 559, 7.5, 46, 2483, false),
  'cosmic_rune -> (27, 564, 8.0, 59, 2484, true),
  'chaos_rune -> (35, 562, 8.5, 74, 2487, true),
  'nature_rune -> (44, 561, 9.0, 91, 2486, true),
  'law_rune -> (54, 563, 9.5, 99, 2485, true),
  'death_rune -> (65, 560, 10.0, 99, 2488, true),
  'blood_rune -> (80, 565, 10.5, 99, 7141, true),
  'soul_rune -> (95, 566, 11.0, 99, 7138, true)
)

/*
 A map of all the altars to the rune type that can be crafted by them.

 altar_object_id -> rune_symbol
*/
private val ALTAR_TO_RUNE = RUNES.map(it => (it._2._5, it._2))


/* Attempt to craft the argued rune. */
private def craftRunes(plr: Player, rune: Rune): Unit = {
  val skill = plr.skill(Skill.RUNECRAFTING)

  val levelRequired = rune._1 /* Are we a high enough level? */
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

    if (rune._5) {
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
  val runeCount = (essenceCount * (skill.getLevel / rune._4)) + essenceCount

  plr.interruptAction()

  plr.inventory.remove(new Item(essenceId, essenceCount))
  plr.inventory.add(new Item(rune._2, runeCount))

  plr.animation(ANIMATION)
  plr.graphic(GRAPHIC)

  skill.addExperience(rune._3 * essenceCount)
}


/* If the object being clicked is an altar, attempt to craft runes of that altar. */
>>[ObjectFirstClickEvent] { (msg, plr) =>
  val altar = ALTAR_TO_RUNE.get(msg.getId)
  if (altar.isDefined) {
    craftRunes(plr, altar.get)
    msg.terminate
  }
}