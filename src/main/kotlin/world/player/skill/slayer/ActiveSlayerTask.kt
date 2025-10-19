package world.player.skill.slayer

import io.luna.game.model.mob.Player
import world.player.skill.slayer.Slayer.activeSlayerTask
import world.player.skill.slayer.Slayer.completedSlayerTasks

/**
 * A class representing an active slayer task.
 *
 * @author lare96
 */
class ActiveSlayerTask(val task: SlayerTaskType, val assignee: SlayerMaster, var remaining: Int) {

    /**
     * Decrements `1` from the remaining amount of tasks. If [remaining] becomes `0` as a result of this, `true` will
     * be returned.
     */
    fun decrement() = --remaining <= 0
}