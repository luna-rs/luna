import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent


/* Equip an item. */
private def equip(msg: EquipItemEvent): Unit = {
  val plr = msg.plr
  plr.interruptAction()
  plr.resetInteractingWith()
  plr.interfaces.close()
  plr.equipment.equip(msg.index)
}

/* Unequip an item. */
private def unequip(msg: WidgetItemFirstClickEvent): Unit = msg.plr.equipment.unequip(msg.index)

/* Listen for equip item event, and equip item. */
on[EquipItemEvent].run { equip }

/* Listen for widget click event, and unequip item. */
on[WidgetItemFirstClickEvent].
  args { 1688 }.
  run { unequip }