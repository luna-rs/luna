package game.skill.crafting.jewelleryMaking

import io.luna.game.model.item.IndexedItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.ClearWidgetItemsMessageWriter
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * A [StandardInterface] implementation that opens up the gold jewellery interface.
 *
 * @author lare96
 */
class GoldJewelleryInterface : StandardInterface(4161) {

    override fun onOpen(plr: Player) {
        val hasRingMould = plr.inventory.contains(GoldJewelleryTable.RINGS.mouldId)
        val hasNecklaceMould = plr.inventory.contains(GoldJewelleryTable.NECKLACES.mouldId)
        val hasAmuletMould = plr.inventory.contains(GoldJewelleryTable.AMULETS.mouldId)

        if (hasRingMould) {
            sendItems(plr, GoldJewelleryTable.RINGS)
        } else {
            resetItems(plr, GoldJewelleryTable.RINGS)
        }

        if (hasNecklaceMould) {
            sendItems(plr, GoldJewelleryTable.NECKLACES)
        } else {
            resetItems(plr, GoldJewelleryTable.NECKLACES)
        }

        if (hasAmuletMould) {
            sendItems(plr, GoldJewelleryTable.AMULETS)
        } else {
            resetItems(plr, GoldJewelleryTable.AMULETS)
        }
    }

    /**
     * Sends a table of items on the interface.
     */
    private fun sendItems(plr: Player, table: GoldJewelleryTable) {
        val interactableItems =
            table.jewelleryItems.mapIndexed { index, jewellery -> IndexedItem(index, jewellery.item) }
        plr.queue(WidgetIndexedItemsMessageWriter(table.buttonWidgetId, interactableItems))
        plr.queue(WidgetTextMessageWriter("", table.textWidgetId))
        plr.queue(WidgetItemModelMessageWriter(table.mouldWidgetId, 0, -1))
    }

    /**
     * Resets a table of items on the interface.
     */
    private fun resetItems(plr: Player, table: GoldJewelleryTable) {
        plr.queue(ClearWidgetItemsMessageWriter(table.buttonWidgetId))
        plr.queue(WidgetTextMessageWriter("You need a ${table.baseName} mould to craft ${table.baseName}s.",
                                          table.textWidgetId))
        plr.queue(WidgetItemModelMessageWriter(table.mouldWidgetId, 100, table.mouldId))
    }
}