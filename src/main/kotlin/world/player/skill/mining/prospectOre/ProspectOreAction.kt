package world.player.skill.mining.prospectOre

import api.predef.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Player
import world.player.Sounds
import world.player.skill.mining.Ore

/**
 * A [LockedAction] that performs ore prospecting for a player.
 *
 * @author lare96
 */
class ProspectOreAction(plr: Player, private val ore: Ore?) : LockedAction(plr) {

    /**
     * When the player will finish prospecting.
     */
    private val end = rand(1, 3)

    override fun run(): Boolean {
        return when (executions) {
            0 -> {
                mob.sendMessage("You examine the rock for ores...")
                false
            }

            end -> {
                when (ore) {
                    null -> mob.sendMessage("There is no ore left in the rock.")
                    else -> mob.sendMessage("This rock contains ${ore.typeName.lowercase()}.")
                }
                mob.playSound(Sounds.PROSPECT_ORE)
                true
            }

            else -> false
        }
    }
}