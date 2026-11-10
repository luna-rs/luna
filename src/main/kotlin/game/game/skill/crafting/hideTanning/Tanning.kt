package game.skill.crafting.hideTanning

import api.bot.isRealBot
import api.bot.action.trading.BotTradeIntentions
import api.predef.numF
import game.bot.scripts.skills.TanHideBotScript
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Executes the native hide conversion and fee in one game-thread transaction.
 * Planned bots retain their exact recipe, finite quantity and shared coin allowance; ordinary players
 * and existing unplanned tanning sessions retain the normal selected-amount behavior.
 *
 * @author lare96
 */
object Tanning {
    /**
     * Pays only an affordable native fee and debits a planned owner's verified payment before returning.
     */
    fun tan(plr: Player, hide: Hide, selectedAmount: Int): Boolean {
        val inventory = plr.inventory
        val stock = inventory.computeAmountForId(hide.hide)
        if (stock == 0) {
            plr.sendMessage("You do not have any of these hides to tan.")
            return false
        }
        var amount = minOf(stock, selectedAmount).coerceAtLeast(0)
        if (amount == 0) return false
        val bot = if (plr.isRealBot()) plr else null
        val owner = bot?.scriptStack?.current() as? TanHideBotScript
        if (bot != null && (owner == null || inventory.any {
                it != null && it.id == hide.hide && it.isDynamic
            })) return false
        val plan = owner?.productionPlan
        val limit = if (plan == null) null else BotTradeIntentions.productionLimit(bot!!, hide.hide)
        if (plan != null) {
            if (plan.recipe.key != "tan:${hide.name}" || plan.recipe.coinCost != hide.cost.toLong() ||
                limit == null || limit.identity !== owner) return false
            amount = minOf(amount, plan.remainingOperations)
            if (amount == 0) return false
        }
        val fee = hide.cost.toLong() * amount
        if (fee > Int.MAX_VALUE || limit != null && fee > limit.maximumCoins) return false
        val coins = inventory.computeAmountForId(995)
        if (fee > coins) {
            plr.sendMessage("You need ${numF((fee - coins).toInt())} more coins to tan these.")
            return false
        }
        if (!inventory.remove(Item(995, fee.toInt()))) return false
        if (limit != null) BotTradeIntentions.recordProductionPurchase(bot!!, hide.hide, fee, limit.identity)
        if (inventory.replace(hide.hide, hide.tan, amount) != amount) return false
        plr.sendMessage("The tanner tans $amount hides for you.")
        return true
    }
}
