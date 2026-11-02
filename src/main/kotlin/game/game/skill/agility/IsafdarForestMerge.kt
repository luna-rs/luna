package game.skill.agility

import io.luna.game.model.chunk.ChunkUpdatableRequest
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.net.msg.out.MergeGameObjectMessageWriter

/**
 * Keeps the native dense forest model around its crossing player during the forced movement window.
 *
 * @author lare96
 */
object IsafdarForestMerge {

    /**
     * Sends one temporary visual merge through the forest object's existing visibility rules.
     */
    fun send(plr: Player, forest: GameObject) {
        queue(plr, forest, 30, 94)
    }

    /**
     * Expires the attached model on its next draw and restores the original scene on the next client cycle.
     */
    fun cancel(plr: Player, forest: GameObject) {
        queue(plr, forest, 0, 0)
    }

    private fun queue(plr: Player, forest: GameObject, startCycle: Int, endCycle: Int) {
        if (plr.index !in 1..2047) return
        val horizontal = forest.direction == ObjectDirection.NORTH || forest.direction == ObjectDirection.SOUTH
        val message = MergeGameObjectMessageWriter(forest.id, forest.objectType.id, forest.direction.id,
            forest.chunk.offset(forest.position), plr.index, startCycle, endCycle,
            if (horizontal) -1 else 0, 2, if (horizontal) 0 else -1, 2)
        forest.chunkRepository.queueUpdate(ChunkUpdatableRequest(forest, message, false))
    }
}
