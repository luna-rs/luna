package world.player.item.equipment

import api.predef.*
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent
import io.luna.game.model.mob.Player

/**
 * Equips the item.
 */
fun equip(plr: Player, index: Int) {
    plr.interruptAction()
    plr.resetInteractingWith()
    plr.interfaces.close()
    plr.equipment.equip(index)
}

/**
 * Unequips the item.
 */
fun unequip(plr: Player, index: Int) = plr.equipment.unequip(index)

/**
 * Listen for equip events.
 */
on(EquipItemEvent::class) { equip(plr, index) }

/**
 * Listen for unequip events.
 */
on(WidgetItemFirstClickEvent::class)
    .filter { widgetId == 1688 }
    .then { unequip(plr, index) }