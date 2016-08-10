/*
 A plugin for the Fishing skill that adds functionality for moving fishing spots.

 SUPPORTS:
  -> Moving fishing spots around at random intervals.
  -> Two possible positions for a fishing spots to be in.

 TODO:
  -> Moving different fishing spots at different times.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.Position
import io.luna.game.model.mobile.Npc


/* Class representing fishing spots in 'FISHING_SPOTS'. */
private case class FishingSpot(id: Int, homePos: Position, awayPos: Position) {
  val npcInstance = new Npc(ctx, id, homePos)
}


/* A range of how often fishing spots will move, in ticks. */
private val MOVE_INTERVAL = 100 to 2000 // Fishing spots will currently move every 1-20 minutes.

/* A Seq of fishing spots that will periodically move. */
private val FISHING_SPOTS = Seq.empty[FishingSpot]


/* Moves fishing spots from their 'home' to 'away' positions, and vice-versa. */
private def moveFishingSpots = {
  for (spot <- FISHING_SPOTS) {
    val spotNpc = spot.npcInstance

    spotNpc.position match {
      case spot.homePos => spotNpc.teleport(spot.awayPos)
      case spot.awayPos => spotNpc.teleport(spot.homePos)
      case _ => fail("invalid fishing spot position")
    }
  }
}


/* Intercept server launch event, perform startup operations for fishing spots. */
intercept[ServerLaunchEvent] { (msg, plr) =>
  if (FISHING_SPOTS.nonEmpty) {
    FISHING_SPOTS.foreach(spot => world.addNpc(spot.npcInstance))

    world.scheduleInterval(MOVE_INTERVAL) { task =>
      moveFishingSpots
    }
  }
}

