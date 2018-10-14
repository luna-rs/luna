import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.{Animation, Graphic, Player}


/* Class representing runes in the 'RUNE_TABLE'. */
private case class Rune(
  id: Int,
  altar: Int,
  multiplier: Int,
  level: Int,
  exp: Double
)


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
  'air_rune -> Rune(id = 556,
    altar = 2478,
    multiplier = 11,
    level = 1,
    exp = 5.0),

  'mind_rune -> Rune(id = 558,
    altar = 2479,
    multiplier = 14,
    level = 2,
    exp = 5.5),

  'water_rune -> Rune(id = 555,
    altar = 2480,
    multiplier = 19,
    level = 5,
    exp = 6.0),

  'earth_rune -> Rune(id = 557,
    altar = 2481,
    multiplier = 26,
    level = 9,
    exp = 6.5),

  'fire_rune -> Rune(id = 554,
    altar = 2482,
    multiplier = 35,
    level = 14,
    exp = 7.0),

  'body_rune -> Rune(id = 559,
    altar = 2483,
    multiplier = 46,
    level = 20,
    exp = 7.5),

  'cosmic_rune -> Rune(id = 564,
    altar = 2484,
    multiplier = 59,
    level = 27,
    exp = 8.0),

  'chaos_rune -> Rune(id = 562,
    altar = 2487,
    multiplier = 74,
    level = 35,
    exp = 8.5),

  'nature_rune -> Rune(id = 561,
    altar = 2486,
    multiplier = 91,
    level = 44,
    exp = 9.0),

  'law_rune -> Rune(id = 563,
    altar = 2485,
    multiplier = 99,
    level = 54,
    exp = 9.5),

  'death_rune -> Rune(id = 560,
    altar = 2488,
    multiplier = 99,
    level = 65,
    exp = 10.0),

  'blood_rune -> Rune(id = 565,
    altar = 7141,
    multiplier = 99,
    level = 80,
    exp = 10.5),

  'soul_rune -> Rune(id = 566,
    altar = 7138,
    multiplier = 99,
    level = 95,
    exp = 11.0)
)

/*
 A different mapping of the 'RUNE_TABLE' that maps altar object identifiers to 'Rune' data.

 altar_id -> Rune
*/
private val ALTAR_TO_RUNE = RUNE_TABLE.values.map(rune => rune.altar -> rune).toMap


/* Attempt to craft the argued rune. */
private def craftRunes(plr: Player, rune: Rune): Unit = {
  val inventory = plr.inventory
  val skill = plr.skill(SKILL_RUNECRAFTING)

  if (skill.getLevel < rune.level) {
    plr.sendMessage(s"You need a Runecrafting level of ${ rune.level } to craft these runes.")
    return
  }

  /*
   Compute essence id and amount, which will result in one of two operations:
     1. If pure essence is required, do a lookup for it
     2. If pure essence isn't required, do a lookup for rune essence
       -> If rune essence isn't found, do a lookup for pure essence
  */
  val (essenceId, essenceCount) = {
    def lookup(id: Int) = inventory.computeAmountForId(id)

    if (rune.level > 20) {
      (PURE_ESSENCE, lookup(PURE_ESSENCE))
    } else {
      val count = lookup(RUNE_ESSENCE)
      if (count > 0) (RUNE_ESSENCE, count) else (PURE_ESSENCE, lookup(PURE_ESSENCE))
    }
  }

  if (essenceCount <= 0) { /* Do we have essence? */
    plr.sendMessage("You need some proper essence in order to craft these runes.")
    return
  }

  /* Compute rune count and craft runes. */
  val runeCount = (essenceCount * (skill.getLevel / rune.multiplier)) + essenceCount

  plr.interruptAction()

  inventory.remove(new Item(essenceId, essenceCount))
  inventory.add(new Item(rune.id, runeCount))

  plr.sendMessage(s"You bind the temple's power into ${ nameOfItem(rune.id) }s.")

  plr.animation(ANIMATION)
  plr.graphic(GRAPHIC)

  skill.addExperience(rune.exp * essenceCount)
}


/* If the object being clicked is an altar, attempt to craft runes of that altar. */
on[ObjectFirstClickEvent] { msg =>
  ALTAR_TO_RUNE.get(msg.id).foreach { it =>
    craftRunes(msg.plr, it)
    msg.terminate
  }
}