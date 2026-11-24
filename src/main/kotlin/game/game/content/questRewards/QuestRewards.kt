package game.content.questRewards

import game.player.Messages
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Checks shared by the NPCs that hand quest rewards back out.
 *
 * @author TheLining
 */
object QuestRewards {

    /**
     * The coins that some of the rewards are bought back with.
     */
    private const val COINS = 995

    /**
     * Whether [plr] has [id] in their inventory, equipment or bank.
     */
    fun owns(plr: Player, id: Int) = plr.inventory.contains(id) || plr.equipment.contains(id) || plr.bank.contains(id)

    /**
     * Whether [plr] carries at least [amount] coins.
     */
    fun canPay(plr: Player, amount: Int) = plr.inventory.computeAmountForId(COINS) >= amount

    /**
     * Gives [plr] one [id], taking [price] coins for it. Returns `false`, having taken nothing, if they can't pay or
     * have no room.
     */
    fun give(plr: Player, id: Int, price: Int = 0): Boolean {
        val reward = Item(id)
        val payment = Item(COINS, price)
        val paysWithWholeStack = price > 0 && plr.inventory.computeAmountForId(COINS) == price
        when {
            price > 0 && !canPay(plr, price) -> return false
            !paysWithWholeStack && !plr.inventory.hasSpaceFor(reward) -> {
                plr.sendMessage(Messages.inventoryFull())
                return false
            }
        }
        if (price > 0 && !plr.inventory.remove(payment)) return false
        if (!plr.inventory.add(reward)) {
            if (price > 0) plr.inventory.add(payment)
            plr.sendMessage(Messages.inventoryFull())
            return false
        }
        return true
    }
}
