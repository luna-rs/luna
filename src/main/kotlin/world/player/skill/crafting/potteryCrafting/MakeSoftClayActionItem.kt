package world.player.skill.crafting.potteryCrafting

import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import world.obj.resource.fillable.WaterResource

/**
 * An [InventoryAction] implementation that makes soft clay.
 */
class MakeSoftClayActionItem(val plr: Player) : InventoryAction(plr, true, 1, 28) {

    /**
     * The unfilled identifier.
     */
    var unfilledId: Int? = null

    override fun executeIf(start: Boolean): Boolean = true

    override fun execute() {
        plr.sendMessage("You combine the clay and water.")
    }

    override fun add(): List<Item> {
        if (unfilledId == null) {
            interrupt()
            return emptyList()
        }
        val newUnfilledId = unfilledId!!
        unfilledId = null
        return listOf(Item(newUnfilledId), Item(1761))
    }

    override fun remove(): List<Item> {
        val remove = getNextResources()
        if (remove == null) {
            interrupt()
            return emptyList()
        }
        unfilledId = remove.first
        return remove.second
    }

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is MakeSoftClayActionItem -> true
            else -> false
        }

    /**
     * Determines the next water resource to use. If the player doesn't have any, interrupts the action.
     */
    private fun getNextResources(): Pair<Int, List<Item>>? {
        val fillMap = WaterResource.FILLABLES
        for (entry in fillMap.entries) {
            val filled = entry.value
            val empty = entry.key
            if (plr.inventory.contains(filled)) {
                val remove = listOf(Item(434), Item(filled))
                return Pair(empty, remove)
            }
        }
        interrupt()
        return null
    }
}