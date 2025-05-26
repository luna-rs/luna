package world.player.skill.slayer

import api.attr.Attr
import api.predef.*
import io.luna.game.model.mob.Player

/**
 * Holds important constants and utility functions related to the Slayer skill.
 */
object Slayer {

    /**
     * An attribute representing a player's active slayer task.
     */
    var Player.activeSlayerTask by Attr.nullableObj(ActiveSlayerTask::class)

    /**
     * Decrement `1` from the remaining amount of tasks. If the remaining amount of monsters is equal or lower to `0`,
     * the task will be completed.
     */
    fun record(plr: Player, npcId: Int) { // TODO implement after combat
        if (plr.activeSlayerTask != null) {
            val (task, xp) = SlayerTask.TASKS[npcId] ?: (null to 0.0)
            if (task != null) {
                if (xp > 0.0) {
                    plr.slayer.addExperience(xp)
                }
                if (--plr.activeSlayerTask!!.remaining <= 0) {
                    plr.sendMessage("task completed")
                    plr.sendMessage("get a new one")
                    plr.activeSlayerTask = null
                }
            }
        }
    }
}