package world.player.skill.crafting.gemCutting

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that cuts precious gems.
 */
class CutGemAction(val plr: Player, val gem: Gem, amount: Int) : InventoryAction(plr, true, 3, amount) {


    override fun remove() = listOf(gem.uncutItem)
    override fun add() = listOf(gem.cutItem)

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.crafting.level < gem.level -> {
                plr.sendMessage("You need a Crafting level of ${gem.level} to cut this.")
                false
            }
            else -> true
        }

    override fun execute() {
        plr.animation(gem.animation)
        plr.sendMessage("You cut the ${itemDef(gem.cut).name}.")

        mob.crafting.addExperience(gem.exp)
    }

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is CutGemAction -> gem == other.gem
            else -> false
        }
}