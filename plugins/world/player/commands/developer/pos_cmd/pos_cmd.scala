import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player


/* Sends a message containing the current position. */
private def currentPos(plr: Player) = plr.sendMessage(plr.position.toString)

/* Sends a message containing the region coordinates. */
private def regionCoords(plr: Player) = plr.sendMessage(plr.position.getRegionCoordinates.toString)

/* Sends a message containing the local region position. */
private def localRegionPos(plr: Player) = {
  val pos = plr.position
  val coords = pos.getRegionCoordinates

  plr.sendMessage(s"LocalRegionPosition{x=${ coords.getLocalX(pos) }, y=${ coords.getLocalY(pos) }}")
}

/* Sends a message containing the local chunk coordinates. */
private def chunkPos(plr: Player) = {
  val pos = plr.position
  plr.sendMessage(s"LocalChunkPosition{x=${ pos.getLocalX }, y=${ pos.getLocalY }}")
}


/* A command that opens an open dialogue for the above functions. */
on[CommandEvent].
  args("pos", RIGHTS_DEV).
  run {
    _.plr.newDialogue.options("Current position", currentPos,
      "Region coordinates", regionCoords,
      "Local region position", localRegionPos,
      "Local chunk position", chunkPos).open()
  }

/* A command that sends the Player a message of their current position. */
on[CommandEvent].
  args("mypos", RIGHTS_DEV).
  run { msg => currentPos(msg.plr) }