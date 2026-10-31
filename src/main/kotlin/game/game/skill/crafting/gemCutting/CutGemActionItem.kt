package game.skill.crafting.gemCutting

import api.predef.*
import game.player.Sound
import game.skill.Skills
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import kotlin.math.floor

/**
 * Cuts one native gem per inventory-action cycle, retaining that cycle's actual product for messages and XP.
 *
 * Opal, jade and red topaz use the Lost City gem-cutting success inputs at the current Crafting level. A crush
 * consumes one uncut gem and produces one crushed gem, awarding quarter XP rounded to a tenth as in the source.
 * Precious gems remain guaranteed successes. The container action computes one outcome per cycle; execution
 * reads that committed outcome rather than rolling a second result.
 *
 * @author lare96
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
        plr.playSound(Sound.CHISEL)
        val crushed = currentAdd.single().id != gem.cut
        if (crushed) {
            plr.sendMessage("You mis-hit the chisel and smash the ${itemName(gem.cut)} to pieces!")
        } else {
            plr.sendMessage("You cut the ${itemName(gem.cut)}.")
        }
        val experience = if (crushed) floor((gem.exp * 10 + 2) / 4) / 10 else gem.exp
        mob.crafting.addExperience(experience)
    }

    /**
     * Rolls the native semi-precious recipe once using the shared skill-success interpolation.
     * Higher levels improve the odds; there is no artificial level-25 guarantee.
     */
    private fun getCutGem(): Item {
        val chance = gem.chance ?: return gem.cutItem
        return if (Skills.success(chance, plr.crafting.level)) gem.cutItem else gem.getCrushedItem()
    }
}
