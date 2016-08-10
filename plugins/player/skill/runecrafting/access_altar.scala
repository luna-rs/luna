import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment.HEAD
import io.luna.game.model.mobile.Player


private case class Altar(
                          talismanId: Int,
                          tiaraId: Int,
                          altarId: Int,
                          portalId: Int,
                          enterPos: Position,
                          exitPos: Position
                        )

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

private val TALISMAN_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.talismanId -> altar).toMap
private val TIARA_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.tiaraId -> altar).toMap
private val PORTAL_TO_ALTAR = ALTAR_TABLE.values.map(altar => altar.portalId -> altar).toMap


private def enterAltar(plr: Player, altar: Altar, msg: Event) = {
  plr.teleport(altar.enterPos)
  plr.sendMessage("") // TODO get proepr message
  msg.terminate
}
private def exitAltar(plr: Player, altar: Altar, msg: Event) = {
  plr.teleport(altar.exitPos)
  plr.sendMessage("") // TODO get proper message
  msg.terminate
}


intercept[ItemOnObjectEvent] { (msg, plr) =>
  TALISMAN_TO_ALTAR.get(msg.getItemId).foreach { altar =>
    if (altar.altarId == msg.getObjectId) {
      enterAltar(plr, altar, msg)
    }
  }
}

intercept[ObjectFirstClickEvent] { (msg, plr) =>
  val headId = plr.inventory.computeIdForIndex(HEAD).orElse(-1)

  TIARA_TO_ALTAR.get(headId).foreach(enterAltar(plr, _, msg))
}

intercept[ObjectFirstClickEvent] { (msg, plr) =>
  PORTAL_TO_ALTAR.get(msg.getId).foreach(exitAltar(plr, _, msg))
}