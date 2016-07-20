/*
 Runecrafting plugin, supports:
  -> Differentiating between rune essence and pure essence
  -> All runes on all altars
  -> Level multipliers (double cosmics, double nats, etc)

 TODO:
  -> Rune pouches
  -> Combination runes (maybe?)
  -> Tiaras (maybe?)
  -> Talismans (maybe?)
*/

import io.luna.game.event.impl.ObjectFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.{Animation, Graphic, Player, Skill}


/* Type alias for rune tuple. */
private type Rune = (Int, Int, Double, Int, Boolean)


/* Rune essence identifier. */
private val RUNE_ESSENCE = 1436

/* Pure essence identifier. */
private val PURE_ESSENCE = 7936

/* Runecrafting animation. */
private val ANIMATION = new Animation(791)

/* Runecrafting graphic. */
private val GRAPHIC = new Graphic(186, 100)

/*
 A map of all the rune types that can be crafted.

 rune_name -> (level, id, experience, rune_multiplier, pure_essence?)
*/
private val RUNES = Map(
  "Air rune" ->(1, 556, 5.0, 11, false),
  "Mind rune" ->(2, 558, 5.5, 14, false),
  "Water rune" ->(5, 555, 6.0, 19, false),
  "Earth rune" ->(9, 557, 6.5, 26, false),
  "Fire rune" ->(14, 554, 7.0, 35, false),
  "Body rune" ->(20, 559, 7.5, 46, false),
  "Cosmic rune" ->(27, 564, 8.0, 59, true),
  "Chaos rune" ->(35, 562, 8.5, 74, true),
  "Nature rune" ->(44, 561, 9.0, 91, true),
  "Law rune" ->(54, 563, 9.5, -1, true),
  "Death rune" ->(65, 560, 10.0, -1, true),
  "Blood rune" ->(80, 565, 10.5, -1, true),
  "Soul rune" ->(95, 566, 11.0, -1, true)
)

/*
 A map of all the altars that can be used to craft runes.

 id -> rune_name
*/
private val ALTARS = Map(
  2478 -> "Air rune",
  2479 -> "Mind rune",
  2480 -> "Water rune",
  2481 -> "Earth rune",
  2482 -> "Fire rune",
  2483 -> "Body rune",
  2484 -> "Cosmic rune",
  2487 -> "Chaos rune",
  2486 -> "Nature rune",
  2485 -> "Law rune",
  2488 -> "Death rune",
  7141 -> "Blood rune",
  7138 -> "Soul rune"
)

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

  plr.inventory.remove(new Item(essenceId, essenceCount))
  plr.inventory.add(new Item(rune._2, runeCount))

  plr.animation(ANIMATION)
  plr.graphic(GRAPHIC)

  skill.addExperience(rune._3 * essenceCount)
}

/*
 If object being clicked is an altar, attempt to craft runes of that altar. Regardless if successful
 or not, terminate the event.
*/
>>[ObjectFirstClickEvent] { (msg, plr) =>
  val altar = ALTARS.get(msg.getId)
  if (altar.isDefined) {
    craftRunes(plr, RUNES(altar.get._1))
    msg.terminate
  }
}