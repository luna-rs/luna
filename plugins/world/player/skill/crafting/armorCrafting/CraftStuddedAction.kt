package world.player.skill.crafting.armorCrafting

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] implementation that will make studded armor.
 */
class CraftStuddedAction(val plr: Player, val armor: HideArmor, val removeId: Int) :
        InventoryAction(plr, true, 2, Int.MAX_VALUE) {

    companion object {

        /**
         * The steel studs identifier.
         */
        val STUDS = 2370
    }

    override fun executeIf(start: Boolean): Boolean =
        when {
            plr.crafting.level < armor.level -> {
                plr.sendMessage("You need a Crafting level of ${armor.level} to make this.")
                false
            }
            else -> true
        }

    override fun execute() {
        plr.crafting.addExperience(armor.exp)
    }

    override fun remove() = listOf(Item(removeId), Item(STUDS))

    override fun add() = listOf(Item(armor.id))

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is CraftStuddedAction -> armor == other.armor
            else -> false
        }
}