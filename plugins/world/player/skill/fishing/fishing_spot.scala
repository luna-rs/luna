import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc


/* Class representing fishing spots in 'FISHING_SPOTS'. */
private case class FishingSpot(id: Int, moveInterval: Range, homePos: Position, awayPos: Position) {
  val npc = new Npc(ctx, id, homePos)
  var elaspedMinutes = 0
  var currentMinutes = pick(moveInterval)

  def shouldMove = {
    elaspedMinutes += 1
    if(elaspedMinutes >= currentMinutes) {
      elaspedMinutes = 0
      currentMinutes = pick(moveInterval)
      true
    }
    false
  }
}


/* A range of how often fishing spots will move, in ticks. */
private val MOVE_INTERVAL = 100 to 500

/* A List of fishing spots that will periodically move. */
private val FISHING_SPOTS = List.empty[FishingSpot]


/* Moves fishing spots from their 'home' to 'away' positions, and vice-versa. */
private def moveFishingSpots() = {
  for (spot <- FISHING_SPOTS if spot.shouldMove) {
    val npc = spot.npc

    npc.position match {
      case spot.homePos => npc.teleport(spot.awayPos)
      case spot.awayPos => npc.teleport(spot.homePos)
      case _ => fail("invalid fishing spot position")
    }
  }
}


/* Schedule a task that attempts to move spot every minute. */
on[ServerLaunchEvent] { msg =>
  if (FISHING_SPOTS.nonEmpty) {
    FISHING_SPOTS.foreach(spot => world.add(spot.npc))

    world.scheduleForever(100) {
      moveFishingSpots()
    }
  }
}
