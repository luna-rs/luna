package world.player.skill.smithing.smithBar

import api.predef.*
import com.google.common.collect.ArrayListMultimap
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.game.model.mob.varp.Varp
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter
import world.player.skill.smithing.BarType

/**
 * A [StandardInterface] that builds and represents the smithing interface.
 */
class SmithingInterface(barType: BarType? = null) : StandardInterface(994) {

    /**
     * The bar currently being represented by the interface.
     */
    private var usingBar = barType

    override fun onOpen(player: Player) {

        // First put all items that need to be displayed into the itemMap.
        val itemMap = ArrayListMultimap.create<Int, IndexedItem>()
        val clearSet = HashSet<SmithingTable>()
        for (table in SmithingTable.VALUES) {
            var added = false
            for (smithItem in table.items) {
                if (smithItem.barType == usingBar) {
                    // The bar of the item matches the bar we're using, add it.
                    itemMap.put(table.widgetId, IndexedItem(table.slotId, smithItem.item))
                    added = true
                }
            }
            if (!added) {
                // We found NO matches for the bar we're using, so we need to clear the widget.
                clearSet.add(table)
            }
        }

        for (entry in itemMap.asMap()) { // Update widgets with correct items.
            player.queue(WidgetIndexedItemsMessageWriter(entry.key, entry.value))
        }
        for (table in clearSet) { // Clear unused widgets.
            player.queue(WidgetIndexedItemsMessageWriter(table.widgetId, IndexedItem(table.slotId, -1, 0)))
        }

        // Varps to make the interface work.
        val barAmount = player.inventory.computeAmountForId(usingBar!!.id)
        player.sendVarp(Varp(210, barAmount))
        player.sendVarp(Varp(211, player.smithing.level))
        player.sendVarp(Varp(262, 1)) // Enable oil lamp.
    }
}