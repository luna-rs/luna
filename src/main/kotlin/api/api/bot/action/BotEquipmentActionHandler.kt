package api.bot.action

import api.bot.SuspendableCondition
import api.predef.*
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.mob.bot.Bot

/**
 * Handles equipment-related actions for a [Bot].
 *
 * This action handler wraps the low-level inventory and equipment widget clicks required to equip and unequip items.
 * Each action waits for the expected equipment state change before returning, allowing bot scripts to treat equipment
 * changes as suspendable, result-based operations.
 *
 * @property bot The bot performing equipment actions.
 * @property handler The parent action handler.
 */
class BotEquipmentActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Attempts to equip the inventory item with the specified [id].
     *
     * The bot first checks whether the item exists in its inventory and whether the item has a valid equipment
     * definition. If both checks pass, an inventory item click is sent and this method suspends until the item appears
     * in its expected equipment slot.
     *
     * @param id The item id to equip.
     * @return `true` if the item was equipped successfully, otherwise `false`.
     */
    suspend fun equip(id: Int): Boolean {
        bot.log("Trying to equip ${itemName(id)}.")

        val index = bot.inventory.computeIndexForId(id)
        if (index == -1) {
            // We don't have the item, invalid item.
            bot.log("Don't have ${itemName(id)}.")
            return false
        }

        val equipmentIndex = EquipmentDefinition.ALL.get(id).map { it.index }
        if (equipmentIndex.isEmpty) {
            // Invalid item.
            bot.log("${itemName(id)} cannot be equipped.")
            return false
        }

        val suspendCond = SuspendableCondition({ bot.equipment[equipmentIndex.get()]?.id == id })
        bot.output.sendInventoryItemClick(2, index, id)
        return suspendCond.submit().await()
    }

    /**
     * Attempts to unequip the item in the specified equipment [index].
     *
     * If the slot is already empty, this method returns `true` because the desired final state has already been met.
     * Otherwise, the bot checks for enough inventory space, sends the equipment widget click, and suspends until the
     * equipment slot becomes empty.
     *
     * @param index The equipment slot index to unequip.
     * @return `true` if the slot is empty after the action, otherwise `false`.
     */
    suspend fun unequip(index: Int): Boolean {
        val item = bot.equipment[index] ?: return true
        if (!bot.inventory.hasSpaceFor(item)) {
            bot.log("Not enough inventory space to unequip $item.")
            return false
        }

        bot.log("Trying to unequip ${item.name}.")

        val suspendableCond = SuspendableCondition { bot.equipment[index] == null }
        bot.output.sendItemWidgetClick(1, index, 1688, item.id)
        return suspendableCond.submit(3).await()
    }

    /**
     * Attempts to unequip every item the bot is currently wearing.
     *
     * This method first checks that the inventory has enough space for all equipped items. If there is enough space,
     * each occupied equipment slot is unequipped one at a time.
     *
     * @return `true` if every equipped item was removed successfully, otherwise `false`.
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
            if (!unequip(index)) {
                return false
            }
        }
        return true
    }

    /**
     * Attempts to equip every item id supplied.
     *
     * Each item is equipped in the order provided. Failed items are logged, but the method continues attempting the
     * remaining items so a partial equipment setup can still be applied.
     *
     * @param ids The item ids to equip.
     * @return `true` if every item was equipped successfully, otherwise `false`.
     */
    suspend fun equipAll(ids: List<Int>): Boolean {
        bot.log("Trying to equip ${ids.size} items.")

        var success = true
        for (next in ids) {
            if (!equip(next)) {
                bot.log("Could not equip ${itemName(next)}.")
                success = false
            }
        }
        return success
    }
}