package engine.widget

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.ArrangeItemEvent
import io.luna.game.model.item.ItemContainer

/**
 * Allows the player to arrange bank slots.
 */
on(ArrangeItemEvent::class, EventPriority.HIGH) {
    val mode = (insertionMode == 1) // 0 = swap, 1 = insert
    val itemContainer: ItemContainer? = when (widgetId) {
        3214 -> plr.inventory
        5382 -> plr.bank
        else -> null
    }
    if (itemContainer != null) {
        if (mode) {
            itemContainer.insert(fromIndex, toIndex)
        } else {
            itemContainer.swap(toIndex, fromIndex)
        }
    }
}