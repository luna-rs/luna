package game.skill.crafting.battlestaffCrafting

import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.mob.Player
import game.skill.crafting.battlestaffCrafting.Battlestaff.Companion.BATTLESTAFF_ITEM

/**
 * An [InventoryAction] implementation that makes battlestaves.
 *
 * @author lare96
 */
class MakeBattlestaffActionItem(val plr: Player, val battlestaff: Battlestaff, amount: Int) :
        InventoryAction(plr, true, 2, amount) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.crafting.level < battlestaff.level -> {
                plr.sendMessage("You need a Crafting level of ${battlestaff.level} to make this.")
                false
            }
            else -> true
        }

    override fun execute() {
        mob.crafting.addExperience(battlestaff.exp)
    }

    override fun add() = listOf(battlestaff.staffItem)
    override fun remove() = listOf(battlestaff.orbItem, BATTLESTAFF_ITEM)
}