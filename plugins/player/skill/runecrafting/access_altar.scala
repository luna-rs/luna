/*
 A plugin related to the Runecrafting skill that adds functionality for entering and exiting altars.

 SUPPORTS:
  -> Entering by using talismans on altar objects.
  -> Entering by first click while wearing tiaras.
  -> Exiting by using the portal within the altar room.

 AUTHOR: lare96
*/

import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment.HEAD
import io.luna.game.model.mobile.{Animation, Player}


/* Class representing altars in the 'ALTAR_TABLE'. */
private case class Altar(
  talismanId: Int,
  tiaraId: Int,
  altarId: Int,
  portalId: Int,
  enterPos: Position,
  exitPos: Position
)


/* Animation that plays when using a talisman on an altar. */
private val TALISMAN_ANIMATION = new Animation(827)

/*
 A table of all the altars that can be entered and exited.

 altar_symbol -> Altar
*/
private val ALTAR_TABLE = Map(
  'air_altar -> Altar(1438, 5527, 2452, 2465, new Position(2841, 4829), new Position(2983, 3292)),
  'mind_altar -> Altar(1448, 5529, 2453, 2466, new Position(2793, 4828), new Position(2980, 3514)),
  'water_altar -> Altar(1444, 5531, 2454, 2467, new Position(2726, 4832), new Position(3187, 3166)),
  'earth_altar -> Altar(1440, 5535, 2455, 2468, new Position(2655, 4830), new Position(3304, 3474)),
  'fire_altar -> Altar(1442, 5537, 2456, 2469, new Position(2574, 4849), new Position(3311, 3256)),
  'body_altar -> Altar(1446, 5533, 2457, 2470, new Position(2524, 4825), new Position(3051, 3445)),
  'cosmic_altar -> Altar(1454, 5539, 2458, 2471, new Position(2142, 4813), new Position(2408, 4379)),
  'chaos_altar -> Altar(1452, 5543, 2461, 2474, new Position(2268, 4842), new Position(3058, 3591)),
  'nature_altar -> Altar(1462, 5541, 2460, 2473, new Position(2400, 4835), new Position(2867, 3019)),
  'law_altar -> Altar(1458, 5545, 2459, 2472, new Position(2464, 4818), new Position(2858, 3379)),
  'death_altar -> Altar(1456, 5547, 2462, 2475, new Position(2208, 4830), new Position(3222, 3222)),
  'blood_altar -> Altar(1450, 5549, 2463, ???, ???, ???),
  'soul_altar -> Altar(1460, 5551, 2464, ???, ???, ???)
)

/*
 A different mapping of the 'ALTAR_TABLE' that maps talisman identifiers to 'Altar' data.

 talisman_id -> Altar
*/
private val TALISMAN_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.talismanId -> altar).toMap

/*
 A different mapping of the 'ALTAR_TABLE' that maps tiara identifiers to 'Altar' data.

 tiara_id -> Altar
*/
private val TIARA_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.tiaraId -> altar).toMap

/*
 A different mapping of the 'ALTAR_TABLE' that maps portal identifiers to 'Altar' data.

 portal_id -> Altar
*/
private val PORTAL_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.portalId -> altar).toMap


/* A function that moves the player inside the altar using a talisman. */
private def enterWithTalisman(plr: Player, altar: Altar, msg: Event) = {
  plr.sendMessage(s"You hold the ${computeItemName(altar.talismanId)} towards the mysterious ruins.")
  plr.animation(TALISMAN_ANIMATION)
  plr.lockMovement

  world.scheduleOnce(3) {
    plr.sendMessage("You feel a powerful force take hold of you...")
    plr.teleport(altar.enterPos)
    plr.unlockMovement
  }

  msg.terminate
}

/* A function that moves the player inside the altar using a tiara. */
private def enterWithTiara(plr: Player, altar: Altar, msg: Event) = {
  plr.sendMessage("You feel a powerful force take hold of you...")
  plr.teleport(altar.enterPos)
  msg.terminate
}

/* A function that moves the player outside the altar. */
private def exitWithPortal(plr: Player, altar: Altar, msg: Event) = {
  plr.sendMessage("You step through the portal...")
  plr.teleport(altar.exitPos)
  msg.terminate
}


/* Intercept event for entering with talismans. */
intercept[ItemOnObjectEvent] { (msg, plr) =>
  TALISMAN_TO_ALTAR.get(msg.getItemId).foreach { altar =>
    if (altar.altarId == msg.getObjectId) {
      enterWithTalisman(plr, altar, msg)
    }
  }
}

/* Intercept event for entering with tiaras. */
intercept[ObjectFirstClickEvent] { (msg, plr) =>
  val headId = plr.inventory.computeIdForIndex(HEAD).orElse(-1)

  TIARA_TO_ALTAR.get(headId).foreach { altar =>
    if (altar.altarId == msg.getObjectId) {
      enterWithTiara(plr, altar, msg)
    }
  }
}

/* Intercept event for exiting through altar portals. */
intercept[ObjectFirstClickEvent] { (msg, plr) =>
  PORTAL_TO_ALTAR.get(msg.getId).foreach(exitWithPortal(plr, _, msg))
}