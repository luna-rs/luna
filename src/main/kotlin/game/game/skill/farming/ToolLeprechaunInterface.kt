package game.skill.farming

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.InventoryOverlayInterface
import io.luna.net.msg.out.WidgetItemsMessageWriter

/**
 * An [InventoryOverlayInterface] implementation representing the tool leprechaun's storage interface. What is stored
 * is shown by the client from the `farming_tools` varp, and what can be stored is counted by the client from the
 * player's inventory.
 *
 * @author hydrozoa
 * @author lare96
 */
class ToolLeprechaunInterface : InventoryOverlayInterface(15614, 15593) {

    companion object {

        /**
         * The widget displaying stored single tools, with the "Remove" option.
         */
        const val REMOVE_SINGLE_WIDGET = 15682

        /**
         * The widget displaying stored bulk tools, with the "Remove 1/5/All/X" options.
         */
        const val REMOVE_BULK_WIDGET = 15683

        /**
         * The inventory side widget displaying single tools, with the "Store" option.
         */
        const val STORE_SINGLE_WIDGET = 15594

        /**
         * The inventory side widget displaying bulk tools, with the "Store 1/5/All/X" options.
         */
        const val STORE_BULK_WIDGET = 15595

        /**
         * Returns the tool displayed at [index] of [widgetId], or `null` if there isn't one.
         */
        fun getTool(widgetId: Int, index: Int): StorableTool? =
            when (widgetId) {
                REMOVE_SINGLE_WIDGET, STORE_SINGLE_WIDGET -> StorableTool.SINGLE.getOrNull(index)
                REMOVE_BULK_WIDGET, STORE_BULK_WIDGET -> StorableTool.BULK.getOrNull(index)
                else -> null
            }
    }

    override fun onOpen(plr: Player) {
        Farming.sendStoredTools(plr)
        refresh(plr)
    }

    /**
     * Stores up to [amount] of [tool] from the player's inventory.
     */
    fun store(plr: Player, tool: StorableTool, amount: Int) {
        val stored = tool.getAmount(plr)
        if (stored >= tool.max) {
            plr.sendMessage("You can't store any more of those.")
            return
        }

        // Prefer storing the fullest watering can.
        val index = tool.items.indexOfLast { plr.inventory.computeNonDynamicAmountForId(it) > 0 }
        if (index == -1) {
            plr.sendMessage("You don't have any of those to store.")
            return
        }
        val id = tool.items[index]
        val count = minOf(amount, tool.max - stored, plr.inventory.computeNonDynamicAmountForId(id))
        if (plr.inventory.remove(Item(id, count))) {
            tool.setValue(plr, if (tool == StorableTool.WATERING_CAN) index + 1 else stored + count)
            Farming.sendStoredTools(plr)
            refresh(plr)
        }
    }

    /**
     * Removes up to [amount] of [tool] from storage, into the player's inventory.
     */
    fun remove(plr: Player, tool: StorableTool, amount: Int) {
        val stored = tool.getAmount(plr)
        if (stored == 0) {
            plr.sendMessage("You don't have any of those stored.")
            return
        }

        val space = plr.inventory.computeRemainingSize()
        if (space == 0) {
            plr.inventory.onCapacityExceeded()
            return
        }
        val id = tool.getDisplayItem(plr)
        val count = minOf(amount, stored, space)
        if (plr.inventory.add(Item(id, count))) {
            tool.setValue(plr, if (tool == StorableTool.WATERING_CAN) 0 else stored - count)
            Farming.sendStoredTools(plr)
            refresh(plr)
        }
    }

    /**
     * Sends the tool icons on both the main and inventory side interfaces. The side interface shows the watering can
     * that would be stored.
     */
    private fun refresh(plr: Player) {
        val heldCan = StorableTool.WATERING_CAN.items.lastOrNull { plr.inventory.computeNonDynamicAmountForId(it) > 0 }
        val stored = StorableTool.SINGLE.map { Item(it.getDisplayItem(plr)) }
        val held = StorableTool.SINGLE.map {
            Item(if (it == StorableTool.WATERING_CAN && heldCan != null) heldCan else it.items.first())
        }
        val bulk = StorableTool.BULK.map { Item(it.items.first()) }
        plr.queue(WidgetItemsMessageWriter(REMOVE_SINGLE_WIDGET, stored))
        plr.queue(WidgetItemsMessageWriter(REMOVE_BULK_WIDGET, bulk))
        plr.queue(WidgetItemsMessageWriter(STORE_SINGLE_WIDGET, held))
        plr.queue(WidgetItemsMessageWriter(STORE_BULK_WIDGET, bulk))
    }
}
