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

 rune_symbol -> (level, id, experience, rune_multiplier, pure_essence?)
*/
private val RUNES = Map(
  'air ->(1, 556, 5.0, 11, false),
  'mind ->(2, 558, 5.5, 14, false),
  'water ->(5, 555, 6.0, 19, false),
  'earth ->(9, 557, 6.5, 26, false),
  'fire ->(14, 554, 7.0, 35, false),
  'body ->(20, 559, 7.5, 46, false),
  'cosmic ->(27, 564, 8.0, 59, true),
  'chaos ->(35, 562, 8.5, 74, true),
  'nature ->(44, 561, 9.0, 91, true),
  'law ->(54, 563, 9.5, -1, true),
  'death ->(65, 560, 10.0, -1, true),
  'blood ->(80, 565, 10.5, -1, true),
  'soul ->(95, 566, 11.0, -1, true)
)

/*
 A map of all the altars that can be used to craft runes.

 id -> rune_symbol
*/
private val ALTARS = Map(
  2478 -> 'air,
  2479 -> 'mind,
  2480 -> 'water,
  2481 -> 'earth,
  2482 -> 'fire,
  2483 -> 'body,
  2484 -> 'cosmic,
  2487 -> 'chaos,
  2486 -> 'nature,
  2485 -> 'law,
  2488 -> 'death,
  7141 -> 'blood,
  7138 -> 'soul
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
    craftRunes(plr, RUNES(altar.get))
    msg.terminate
  }
}