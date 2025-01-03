package world.player.skill.crafting.gemCutting

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that cuts precious gems.
 */
class CutGemActionItem(val plr: Player, val gem: Gem, amount: Int) : InventoryAction(plr, true, 3, amount) {

    override fun remove() = listOf(gem.uncutItem)
    override fun add() = listOf(getCutGem())

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
        plr.sendMessage("You cut the ${itemName(gem.cut)}.")

        mob.crafting.addExperience(gem.exp)
    }

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is CutGemActionItem -> gem == other.gem
            else -> false
        }

    /**
     * Gets the cut gem item. Used for a chance of getting a crushed gem, when cutting semi-precious gems.
     */
    private fun getCutGem(): Item {
        if (gem.isSemiPrecious()) {
            // No longer get crushed gems after level 20ish
            val baseRoll = 75
            val finalRoll = baseRoll + plr.crafting.level
            return if (rand(1, 100) < finalRoll) gem.cutItem else gem.getCrushedItem()
        }
        return gem.cutItem
    }
}