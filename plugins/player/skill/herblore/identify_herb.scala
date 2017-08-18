/*
 A plugin for the Herblore skill that adds functionality for identifying herbs.

 SUPPORTS:
  -> Identifying all herbs.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player


/* Class representing herbs in the 'HERB_TABLE'. */
private case class Herb(unidentified: Int, identified: Int, level: Int, exp: Double)


/*
 A table of all the herbs that can be identified.

 herb_symbol -> Herb
*/
private val HERB_TABLE = Map(
  'guam_leaf -> Herb(unidentified = 199,
    identified = 249,
    level = 3,
    exp = 2.5),

  'marrentill -> Herb(unidentified = 201,
    identified = 251,
    level = 5,
    exp = 3.8),

  'tarromin -> Herb(unidentified = 203,
    identified = 253,
    level = 11,
    exp = 5.0),

  'harralander -> Herb(unidentified = 205,
    identified = 255,
    level = 20,
    exp = 6.3),

  'ranarr_weed -> Herb(unidentified = 207,
    identified = 257,
    level = 25,
    exp = 7.5),

  'toadflax -> Herb(unidentified = 3049,
    identified = 2998,
    level = 30,
    exp = 8.0),

  'irit_leaf -> Herb(unidentified = 209,
    identified = 259,
    level = 40,
    exp = 8.8),

  'avantoe -> Herb(unidentified = 211,
    identified = 261,
    level = 48,
    exp = 10.0),

  'kwuarm -> Herb(unidentified = 213,
    identified = 263,
    level = 54,
    exp = 11.3),

  'snapdragon -> Herb(unidentified = 3051,
    identified = 3000,
    level = 59,
    exp = 11.8),

  'cadantine -> Herb(unidentified = 215,
    identified = 265,
    level = 65,
    exp = 12.5),

  'lantadyme -> Herb(unidentified = 2485,
    identified = 2481,
    level = 67,
    exp = 13.1),

  'dwarf_weed -> Herb(unidentified = 217,
    identified = 267,
    level = 70,
    exp = 13.8),

  'torstol -> Herb(unidentified = 219,
    identified = 269,
    level = 75,
    exp = 15.0)
)

/*
 A different mapping of the 'HERB_TABLE' that maps unidentified herbs to their data.

 unidentified_id -> Herb
*/
private val UNIDENTIFIED_TO_HERB = HERB_TABLE.values.map(herb => herb.unidentified -> herb).toMap


/* Attempt to identify the unidentified herb. */
private def identifyHerb(plr: Player, herb: Herb) {
  val skill = plr.skill(SKILL_HERBLORE)

  val levelRequired = herb.level
  if (skill.getLevel >= levelRequired) {
    plr.interruptAction()

    plr.inventory.remove(new Item(herb.unidentified))
    plr.inventory.add(new Item(herb.identified))

    skill.addExperience(herb.exp)

    plr.sendMessage(s"You identify the ${ nameOfItem(herb.identified) }.")
  } else {
    plr.sendMessage(s"You need a Herblore level of $levelRequired to identify this herb.")
  }
}


/* If the item clicked is an unidentified herb, identify it. */
on[ItemFirstClickEvent] { msg =>
  UNIDENTIFIED_TO_HERB.get(msg.id).foreach { it =>
    identifyHerb(msg.plr, it)
    msg.terminate
  }
}
