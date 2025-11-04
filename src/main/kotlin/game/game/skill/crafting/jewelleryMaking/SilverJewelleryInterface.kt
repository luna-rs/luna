package game.skill.crafting.jewelleryMaking

import io.luna.game.model.item.IndexedItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter

/**
 * A [StandardInterface] implementation that opens up the silver jewellery interface.
 *
 * @author lare96
 */
class SilverJewelleryInterface : StandardInterface(13782) {

    override fun onOpen(plr: Player) {
        for (table in SilverJewelleryTable.VALUES) {
            val item = IndexedItem(0, if (plr.inventory.contains(table.mouldId))
                table.jewelleryItem.item.id else table.mouldId)
            plr.queue(WidgetIndexedItemsMessageWriter(table.mouldWidgetId, item))
        }
    }
}