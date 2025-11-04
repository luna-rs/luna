package engine.widget

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent

/**
 * Equips the item.
 */
on(EquipItemEvent::class, EventPriority.HIGH) {
    plr.resetInteractingWith()
    plr.overlays.closeWindows()
    plr.equipment.equip(index)
}

/**
 * Unequips the item.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 1688 }
    .then { plr.equipment.unequip(index) }