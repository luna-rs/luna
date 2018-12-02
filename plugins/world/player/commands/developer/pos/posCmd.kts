import api.*
import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.mob.Player

/**
 * Sends a message containing the absolute position.
 */
fun currentPos(plr: Player) = plr.sendMessage(plr.position.toString())

/**
 * Sends a message containing the chunk position.
 */
fun chunkPos(plr: Player) = plr.sendMessage(plr.position.chunkPosition.toString())

/**
 * Sends a message containing the local chunk position.
 */
fun localChunkPos(plr: Player) {
    val pos = plr.position
    val chunkPos = pos.chunkPosition
    val chunkX = chunkPos.getLocalX(pos)
    val chunkY = chunkPos.getLocalY(pos)

    plr.sendMessage("LocalChunkPosition{x=$chunkX, y=$chunkY}")
}

/**
 * Sends a message containing the region position.
 */
fun regionPos(plr: Player) = plr.sendMessage(plr.position.regionPosition.toString())

/**
 * Sends a message containing the local region position.
 */
fun localRegionPos(plr: Player) {
    val pos = plr.position
    val region = plr.position.regionPosition
    plr.sendMessage("LocalRegionPosition{x=${region.getLocalX(pos)}, y=${region.getLocalY(pos)}}")
}

/**
 * A listener for the "pos" command.
 */
on(CommandEvent::class)
    .args("pos", RIGHTS_DEV)
    .run { msg ->
        msg.plr.newDialogue().options(
                "Position", { currentPos(it) },
                "ChunkPosition", { chunkPos(it) },
                "ChunkPosition (local)", { localChunkPos(it) },
                "RegionPosition", { regionPos(it) },
                "RegionPosition (local)", { localRegionPos(it) }).open()
    }

/**
 * A listener for the "mypos" command.
 */
on(CommandEvent::class)
    .args("mypos", RIGHTS_DEV)
    .run { currentPos(it.plr) }