package game.skill.crafting.potteryCrafting

import game.obj.resource.fillable.WaterResource
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] implementation that makes soft clay.
 *
 * @author lare96
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
            complete()
            return emptyList()
        }
        val newUnfilledId = unfilledId!!
        unfilledId = null
        return listOf(Item(newUnfilledId), Item(1761))
    }

    override fun remove(): List<Item> {
        val remove = getNextResources()
        if (remove == null) {
            complete()
            return emptyList()
        }
        unfilledId = remove.first
        return remove.second
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
        complete()
        return null
    }
}