package api.bot.action

import api.bot.SuspendableCondition
import api.bot.SuspendableFuture
import api.bot.SuspendableFuture.SuspendableFutureFailed
import api.bot.SuspendableFuture.SuspendableFutureSuccess
import api.predef.*
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.mob.bot.Bot

/**
 * A [BotEquipmentActionHandler] implementation for equipment related actions.
 */
class BotEquipmentActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * An action that forces a [Bot] to equip the item with [id]. Will unsuspend when the item has been equipped.
     */
    fun equip(id: Int): SuspendableFuture {
        bot.log("Trying to equip ${itemName(id)}.")
        val index = bot.inventory.computeIndexForId(id)
        if (index.isEmpty) {
            // We don't have the item, invalid item.
            bot.log("Don't have ${itemName(id)}.")
            return SuspendableFutureFailed
        }
        val equipmentIndex = EquipmentDefinition.ALL.get(id).map { it.index }
        if (equipmentIndex.isEmpty) {
            // Invalid item.
            bot.log("${itemName(id)} cannot be equipped.")
            return SuspendableFutureFailed
        }
        val suspendCond = SuspendableCondition({ bot.equipment[equipmentIndex.get()]?.id == id })
        bot.output.sendInventoryItemClick(2, index.asInt, id)
        return suspendCond.submit()
    }

    /**
     * An action that forces a [Bot] to remove their equipment on [index]. Will unsuspend when the equipment has been
     * removed.
     */
    fun unequip(index: Int): SuspendableFuture {
        val item = bot.equipment[index]
        if (item == null) {
            return SuspendableFutureSuccess
        }
        if (!bot.inventory.hasSpaceFor(item)) {
            bot.log("Not enough inventory space to unequip $item.")
            return SuspendableFutureFailed
        }
        bot.log("Trying to unequip ${itemName(item)}.")
        val suspendableCond = SuspendableCondition { bot.equipment[index] == null }
        bot.output.sendItemWidgetClick(1, index, 1688, item.id)
        return suspendableCond.submit(3)
    }

    /**
     * Forces a [Bot] to unequip everything they are currently wearing.
     *
     * @return `true` if everything was unequipped.
     */
    suspend fun unequipAll(): Boolean {
        if (!bot.inventory.hasSpaceForAll(bot.equipment)) {
            bot.log("Not enough inventory space to unequip everything.")
            return false
        }
        bot.log("Trying unequip all items.")
        for ((index, item) in bot.equipment.withIndex()) {
            if (item == null) {
                continue
            }
            if (!unequip(index).await()) {
                return false
            }
        }
        return true
    }

    /**
     * Forces a [Bot] to equip all [ids].
     *
     * @param ids The item IDs to equip.
     * @return `true` if everything was equipped.
     */
    suspend fun equipAll(vararg ids: Int): Boolean {
        bot.log("Trying to equip ${ids.size} items.")
        var success = true
        for (next in ids) {
            if (!equip(next).await()) {
                bot.log("Could not equip ${itemName(next)}.")
                success = false
            }
        }
        return success
    }
}