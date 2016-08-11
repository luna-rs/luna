/*
 A plugin for the Herblore skill that adds functionality for identifying herbs.

 SUPPORTS:
  -> Identifying all herbs.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Player
import io.luna.game.model.mobile.Skill.HERBLORE


/* Class representing herbs in the 'HERB_TABLE'. */
private case class Herb(unidentifiedId: Int, identifiedId: Int, level: Int, exp: Double)


/*
 A table of all the herbs that can be identified.

 herb_symbol -> Herb
*/
private val HERB_TABLE = Map(
  'guam_leaf -> Herb(199, 249, 3, 2.5),
  'marrentill -> Herb(201, 251, 5, 3.8),
  'tarromin -> Herb(203, 253, 11, 5.0),
  'harralander -> Herb(205, 255, 20, 6.3),
  'ranarr_weed -> Herb(207, 257, 25, 7.5),
  'toadflax -> Herb(3049, 2998, 30, 8.0),
  'irit_leaf -> Herb(209, 259, 40, 8.8),
  'avantoe -> Herb(211, 261, 48, 10.0),
  'kwuarm -> Herb(213, 263, 54, 11.3),
  'snapdragon -> Herb(3051, 3000, 59, 11.8),
  'cadantine -> Herb(215, 265, 65, 12.5),
  'lantadyme -> Herb(2485, 2481, 67, 13.1),
  'dwarf_weed -> Herb(217, 267, 70, 13.8),
  'torstol -> Herb(219, 269, 75, 15.0)
)

/*
 A different mapping of the 'HERB_TABLE' that maps unidentified herbs to their data.

 unidentified_id -> Herb
*/
private val UNIDENTIFIED_TO_HERB = HERB_TABLE.values.map(herb => herb.unidentifiedId -> herb).toMap


/* Attempt to identify the unidentified herb. */
private def identifyHerb(plr: Player, herb: Herb) {
  val skill = plr.skill(HERBLORE)

  val levelRequired = herb.level
  if (skill.getLevel >= levelRequired) {
    plr.interruptAction()

    plr.inventory.remove(new Item(herb.unidentifiedId))
    plr.inventory.add(new Item(herb.identifiedId))

    skill.addExperience(herb.exp)

    plr.sendMessage(s"You identify the ${computeItemName(herb.identifiedId)}.")
  } else {
    plr.sendMessage(s"You need a Herblore level of $levelRequired to identify this herb.")
  }
}


/* If the item clicked is an unidentified herb, identify it. */
intercept[ItemFirstClickEvent] { (msg, plr) =>
  UNIDENTIFIED_TO_HERB.get(msg.getId).foreach { it =>
    identifyHerb(plr, it)
    msg.terminate
  }
}
