import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment.HEAD
import io.luna.game.model.mob.{Animation, Player}


/* Class representing altars in the 'ALTAR_TABLE'. */
private case class Altar(
  talisman: Int,
  tiara: Int,
  altar: Int,
  portal: Int,
  enter: Position,
  exit: Position
)


/* Animation that plays when using a talisman on an altar. */
private val TALISMAN_ANIMATION = new Animation(827)

/*
 A table of all the altars that can be entered and exited.

 altar_symbol -> Altar
*/
// TODO "altar" ids are wrong. Find proper object ids
private val ALTAR_TABLE = Map(
  'air_altar -> Altar(talisman = 1438,
    tiara = 5527,
    altar = 2452,
    portal = 2465,
    enter = new Position(2841, 4829),
    exit = new Position(2983, 3292)),

  'mind_altar -> Altar(talisman = 1448,
    tiara = 5529,
    altar = 2453,
    portal = 2466,
    enter = new Position(2793, 4828),
    exit = new Position(2980, 3514)),

  'water_altar -> Altar(talisman = 1444,
    tiara = 5531,
    altar = 2454,
    portal = 2467,
    enter = new Position(2726, 4832),
    exit = new Position(3187, 3166)),

  'earth_altar -> Altar(talisman = 1440,
    tiara = 5535,
    altar = 2455,
    portal = 2468,
    enter = new Position(2655, 4830),
    exit = new Position(3304, 3474)),

  'fire_altar -> Altar(talisman = 1442,
    tiara = 5537,
    altar = 2456,
    portal = 2469,
    enter = new Position(2574, 4849),
    exit = new Position(3311, 3256)),

  'body_altar -> Altar(talisman = 1446,
    tiara = 5533,
    altar = 2457,
    portal = 2470,
    enter = new Position(2524, 4825),
    exit = new Position(3051, 3445)),

  'cosmic_altar -> Altar(talisman = 1454,
    tiara = 5539,
    altar = 2458,
    portal = 2471,
    enter = new Position(2142, 4813),
    exit = new Position(2408, 4379)),

  'chaos_altar -> Altar(talisman = 1452,
    tiara = 5543,
    altar = 2461,
    portal = 2474,
    enter = new Position(2268, 4842),
    exit = new Position(3058, 3591)),

  'nature_altar -> Altar(talisman = 1462,
    tiara = 5541,
    altar = 2460,
    portal = 2473,
    enter = new Position(2400, 4835),
    exit = new Position(2867, 3019)),

  'law_altar -> Altar(talisman = 1458,
    tiara = 5545,
    altar = 2459,
    portal = 2472,
    enter = new Position(2464, 4818),
    exit = new Position(2858, 3379)),

  'death_altar -> Altar(talisman = 1456,
    tiara = 5547,
    altar = 2462,
    portal = 2475,
    enter = new Position(2208, 4830),
    exit = new Position(3222, 3222))
)

/*
 A different mapping of the 'ALTAR_TABLE' that maps talisman identifiers to 'Altar' data.

 talisman_id -> Altar
*/
private val TALISMAN_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.talisman -> altar).toMap

/*
 A different mapping of the 'ALTAR_TABLE' that maps tiara identifiers to 'Altar' data.

 tiara_id -> Altar
*/
private val TIARA_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.tiara -> altar).toMap

/*
 A different mapping of the 'ALTAR_TABLE' that maps portal identifiers to 'Altar' data.

 portal_id -> Altar
*/
private val PORTAL_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.portal -> altar).toMap


/* A function that moves the player inside the altar using a talisman. */
private def enterWithTalisman(plr: Player, altar: Altar, msg: Event) = {
  plr.sendMessage(s"You hold the ${ nameOfItem(altar.talisman) } towards the mysterious ruins.")
  plr.animation(TALISMAN_ANIMATION)
  plr.lockMovement

  world.scheduleOnce(3) {
    plr.sendMessage("You feel a powerful force take hold of you...")
    plr.teleport(altar.enter)
    plr.unlockMovement
  }

  msg.terminate
}

/* A function that moves the player inside the altar using a tiara. */
private def enterWithTiara(plr: Player, altar: Altar, msg: Event) = {
  plr.sendMessage("You feel a powerful force take hold of you...")
  plr.teleport(altar.enter)
  msg.terminate
}

/* A function that moves the player outside the altar. */
private def exitWithPortal(plr: Player, altar: Altar, msg: Event) = {
  plr.sendMessage("You step through the portal...")
  plr.teleport(altar.exit)
  msg.terminate
}


/* Intercept event for entering with talismans. */
on[ItemOnObjectEvent] { msg =>
  TALISMAN_TO_ALTAR.get(msg.itemId).foreach { altar =>
    if (altar.altar == msg.objectId) {
      enterWithTalisman(msg.plr, altar, msg)
    }
  }
}

/* Intercept event for entering with tiaras. */
on[ObjectFirstClickEvent] { msg =>
  val headId = msg.plr.inventory.computeIdForIndex(HEAD).orElse(-1)

  TIARA_TO_ALTAR.get(headId).foreach { altar =>
    if (altar.altar == msg.id) {
      enterWithTiara(msg.plr, altar, msg)
  }
  }
}

/* Intercept event for exiting through altar portals. */
on[ObjectFirstClickEvent] { msg =>
  PORTAL_TO_ALTAR.get(msg.id).foreach(exitWithPortal(msg.plr, _, msg))
}