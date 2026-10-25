package game.skill.mining.prospectOre

import api.predef.*
import game.player.Sound
import game.skill.mining.Ore
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Player

/**
 * A [LockedAction] that performs ore prospecting for a player. The prospecting sound plays on the tick after the
 * player starts examining the rock, and the rock's ore is named two ticks later.
 *
 * @author lare96
 */
class ProspectOreAction(plr: Player, private val ore: Ore?) : LockedAction(plr) {

    override fun run(): Boolean {
        when (executions) {
            0 -> mob.sendMessage("You examine the rock for ores...")
            1 -> mob.playSound(Sound.PROSPECT)
            3 -> {
                when (ore) {
                    null -> mob.sendMessage("There is no ore left in the rock.")
                    else -> mob.sendMessage("This rock contains ${ore.typeName.lowercase()}.")
                }
                return true
            }
        }
        return false
    }
}
