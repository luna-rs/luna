package engine.widget

import api.predef.*
import game.content.mageArena.MageArena
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent

/**
 * Equips the item.
 */
on(EquipItemEvent::class, EventPriority.HIGH) {
    if (!MageArena.canEquip(plr, itemId)) return@on
    if (!preservesCombat()) plr.interact(null)
    plr.overlays.closeWindows(!preservesCombat())
    plr.equipment.equip(index)
}

/**
 * Unequips the item.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 1688 }
    .then { plr.equipment.unequip(index) }
