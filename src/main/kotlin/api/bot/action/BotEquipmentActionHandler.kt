package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.predef.*
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.mob.bot.Bot

/**
 * A [BotEquipmentActionHandler] implementation for equipment related actions.
 */
class BotEquipmentActionHandler(bot: Bot) : BotActionHandler(bot) {

    /**
     * An action that forces a [Bot] to equip the item with [id]. Will unsuspend when the item has been equipped.
     */
    fun equip(id: Int): SuspendableFuture {
        val index = bot.inventory.computeIndexForId(id)
        if (index.isEmpty) {
            // We don't have the item, invalid item.
            return SuspendableFuture().signal(false)
        }
        val equipmentIndex = EquipmentDefinition.ALL.get(id).map { it.index }
        if(equipmentIndex.isEmpty) {
            // Invalid item.
            return SuspendableFuture().signal(false)
        }
        val suspendCond = SuspendableCondition({ bot.equipment[equipmentIndex.get()]?.id == id })
        output.sendEquipItem(index.asInt, id)
        return suspendCond.submit()
    }

    /**
     * An action that forces a [Bot] to remove their equipment on [index]. Will unsuspend when the equipment has been
     * removed.
     */
    fun unequip(index: Int): SuspendableFuture {
        val itemId = bot.equipment.computeIdForIndex(index)
        if (!itemId.isPresent) {
            return SuspendableFuture().signal(false)
        }
        val suspendableCond = SuspendableCondition({ bot.equipment[index] == null }, 5)
        output.sendItemWidgetClick(1, index, 1688, itemId.asInt)
        return suspendableCond.submit()
    }
}