package engine.bot.gear

import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalMicroDelay
import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Locates and equips a resolved bot equipment layout.
 *
 * This class represents the execution side of bot gear selection. The selector decides which item ids should be worn,
 * while this locator handles moving the bot to a bank, clearing current equipment, withdrawing the selected items, and
 * equipping them.
 *
 * The [equipment] array is indexed by equipment slot. Each non-null entry is the item id that should be equipped in that
 * slot. Null entries are ignored, allowing selectors to request partial loadouts when a full outfit is not required.
 *
 * @param bot The bot that should locate and equip the selected gear.
 * @param equipment The desired equipment item ids, indexed by equipment slot.
 * @author lare96
 */
class BotGearLocator(val bot: Bot, val equipment: Array<Int?>) {

    /**
     * Locates the selected equipment and equips it onto the bot.
     *
     * This method performs a bank-centered equipment reset:
     * - Travels to a bank and deposits carried items.
     * - Moves currently equipped items into the inventory using a fast memory transfer.
     * - Clears the current equipment container.
     * - Deposits the unequipped items.
     * - Withdraws each selected gear item from the bank.
     * - Equips all selected items.
     *
     * Stackable selected items are withdrawn with a default amount of `500`; non-stackable selected items are withdrawn
     * with an amount of `1`.
     *
     * @return `true` if the bot successfully withdrew and equipped the selected gear, otherwise `false`.
     */
    suspend fun locateAndEquip(): Boolean {
        val banking = bot.actionHandler.banking

        bot.log("Starting equipment selection locator.")
        if (!banking.travelToBankDepositAll()) {
            return false
        }

        // Fast memory based unequip to speed things up here.
        bot.log("Fast transfer: equipment -> inventory.")
        bot.inventory.addAll(bot.equipment)
        bot.equipment.clear()
        bot.naturalDelay()

        if (!banking.travelToBankDepositAll()) {
            return false
        }

        bot.naturalMicroDelay()
        bot.log("Withdrawing all selected gear from bank.")

        val selectedItems = ArrayList<Item>()
        for (id in equipment) {
            if (id != null) {
                selectedItems += Item(id, if (itemDef(id).isStackable) 500 else 1)
            }
        }

        if (!banking.withdrawAll(selectedItems)) {
            return false
        }

        bot.naturalMicroDelay()
        bot.log("Equipping all selected gear.")

        return bot.actionHandler.equipment.equipAll(selectedItems.map { it.id })
    }
}