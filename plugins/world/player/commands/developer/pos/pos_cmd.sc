import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player


/* Sends a message containing the absolute position. */
private def currentPos(plr: Player) = plr.sendMessage(plr.position.toString)

/* Sends a message containing the chunk position. */
private def chunkPos(plr: Player) = plr.sendMessage(plr.position.getChunkPosition.toString)

/* Sends a message containing the local chunk position. */
private def localChunkPos(plr: Player) = {
  val pos = plr.position
  val coords = pos.getChunkPosition

  plr.sendMessage(s"LocalChunkPosition{x=${ coords.getLocalX(pos) }, y=${ coords.getLocalY(pos) }}")
}

/* Sends a message containing the region position. */
private def regionPos(plr: Player) = plr.sendMessage(plr.position.getRegionPosition.toString)

/* Sends a message containing the local region position. */
private def localRegionPos(plr: Player) = {
  val pos = plr.position
  val region = plr.position.getRegionPosition
  plr.sendMessage(s"LocalRegionPosition{x=${ region.getLocalX(pos) }, y=${ region.getLocalY(pos) }}")
}


/* A command that opens an open dialogue for the above functions. */
on[CommandEvent].
  args("pos", RIGHTS_DEV).
  run {
    _.plr.newDialogue.options("Position", currentPos,
      "ChunkPosition", chunkPos,
      "ChunkPosition (local)", localChunkPos,
      "RegionPosition", regionPos,
      "RegionPosition (local)", localRegionPos).open()
  }

/* A command that sends the Player a message of their current position. */
on[CommandEvent].
  args("mypos", RIGHTS_DEV).
  run { msg => currentPos(msg.plr) }