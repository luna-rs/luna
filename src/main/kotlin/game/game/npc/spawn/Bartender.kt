package game.npc.spawn

import game.content.treasureTrails.TrailClues
import game.player.Messages
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Sales shared by the bartenders and innkeepers who sell drinks through their dialogue.
 *
 * TODO Alfred Grimhand's Barcrawl: while the player carries a barcrawl card, each bar adds an option to buy its
 *  barcrawl drink and sign the card. Until the miniquest exists nobody has a card, so no bar offers it.
 */
object Bartender {

    /**
     * The coins drinks are paid with.
     */
    const val COINS = 995

    /**
     * Whether [plr] carries at least [price] coins.
     */
    fun canPay(plr: Player, price: Int) = plr.inventory.computeAmountForId(COINS) >= price

    /**
     * Sells [plr] [amount] of [id] for [price] coins. Returns `false`, having taken nothing, if they can't pay or have
     * no room.
     */
    fun sell(plr: Player, id: Int, price: Int, amount: Int = 1): Boolean {
        val bought = Item(id, amount)
        val payment = Item(COINS, price)
        val paysWithWholeStack = plr.inventory.computeAmountForId(COINS) == price
        when {
            !canPay(plr, price) -> return false
            !paysWithWholeStack && !plr.inventory.hasSpaceFor(bought) -> {
                plr.sendMessage(Messages.inventoryFull())
                return false
            }
        }
        if (!plr.inventory.remove(payment)) return false
        if (!plr.inventory.add(bought)) {
            plr.inventory.add(payment)
            plr.sendMessage(Messages.inventoryFull())
            return false
        }
        return true
    }

    /**
     * Sells [plr] one [id] for [price] coins and sends [boughtMessage], or has them say [poorLine] if they can't
     * afford it.
     */
    fun serve(plr: Player, id: Int, price: Int, poorLine: String, boughtMessage: String) {
        when {
            !canPay(plr, price) -> plr.newDialogue().player(Expression.SAD, poorLine).open()
            sell(plr, id, price) -> plr.sendMessage(boughtMessage)
        }
    }

    /**
     * Whether [plr] carries a treasure trail clue that sends them to [npcId]. The clue's own dialogue then takes the
     * place of the NPC's.
     */
    fun holdsClueFor(plr: Player, npcId: Int) =
        TrailClues.all.any { it.npcId == npcId && plr.inventory.contains(it.id) }
}
