package world.player.skill.crafting.battlestaffCrafting

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.mob.Player
import world.player.skill.crafting.battlestaffCrafting.Battlestaff.Companion.BATTLESTAFF_ITEM

/**
 * An [InventoryAction] implementation that makes battlestaves.
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

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is MakeBattlestaffActionItem -> battlestaff == other.battlestaff
            else -> false
        }
}