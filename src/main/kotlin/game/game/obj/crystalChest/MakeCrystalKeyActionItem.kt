package game.obj.crystalChest

import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] implementation that will join crystal key halves together.
 *
 * @author lare96
 */
class MakeCrystalKeyActionItem(plr: Player, amount: Int) : InventoryAction(plr, true, 1, amount) {

    override fun execute() {
        mob.sendMessage("You join the two halves of the key together.")
    }

    override fun add(): MutableList<Item> = arrayListOf(Item(989))

    override fun remove(): MutableList<Item> = arrayListOf(Item(985), Item(987))
}