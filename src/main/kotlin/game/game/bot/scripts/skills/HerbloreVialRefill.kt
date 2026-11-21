package game.bot.scripts.skills

import api.bot.action.BotSupplySources
import api.bot.action.stockIdFor
import api.bot.script.BotScript.ActionSafety
import api.bot.script.ProductionPlan
import api.predef.itemDef
import game.skill.herblore.makeUnfPotion.UnfPotion
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Quotes empty vial supply and buys only the deficit for a finite, owned-ingredient potion batch.
 *
 * @author lare96
 */
internal object HerbloreVialRefill {
    const val EMPTY_VIAL = 229
    private const val WATER = UnfPotion.VIAL_OF_WATER
    private val coins = Currency.COINS.id

    fun owned(bot: Bot, id: Int): Long {
        val note = itemDef(id).notedId.orElse(-1)
        return bot.bank.computeNonDynamicAmountForId(id).toLong() +
            bot.inventory.computeNonDynamicAmountForId(id) +
            if (note < 0) 0 else bot.inventory.computeNonDynamicAmountForId(note)
    }

    fun usableOperations(bot: Bot, plan: ProductionPlan, includeWater: Boolean): Int {
        var usable = plan.operations
        for ((id, amount) in plan.targets()) {
            if (id == WATER && !includeWater) continue
            val perOperation = (amount / plan.operations).coerceAtLeast(1)
            usable = minOf(usable.toLong(), owned(bot, id) / perOperation).toInt()
        }
        return usable
    }

    suspend fun bankNotes(bot: Bot, plan: ProductionPlan): Boolean {
        val remaining = plan.deadline - System.currentTimeMillis()
        if (remaining <= 0L) return false
        if ((plan.targets().keys + EMPTY_VIAL).none { id ->
                val note = itemDef(id).notedId.orElse(-1)
                note >= 0 && bot.inventory.contains(note)
            }) return true
        return withTimeoutOrNull(remaining) {
            ActionSafety.NON_COMBAT.await(bot) && bot.actionHandler.banking.travelToBankDepositAll()
        } == true
    }

    suspend fun buyMissing(bot: Bot, plan: ProductionPlan) {
        fun missing() = (plan.target(WATER) - owned(bot, WATER) - owned(bot, EMPTY_VIAL))
            .coerceAtLeast(0L).toInt()
        fun safe() = System.currentTimeMillis() < plan.deadline && ActionSafety.NON_COMBAT.allows(bot)
        val ceiling = plan.maximumUnitPrices.getValue(WATER).coerceAtMost(Int.MAX_VALUE.toLong())
        val allowance = minOf(plan.remainingCoins, owned(bot, coins), missing() * ceiling, Int.MAX_VALUE.toLong()).toInt()
        if (allowance < 1 || !safe()) return
        val handler = bot.actionHandler
        val shops = BotSupplySources.generalStores(bot, EMPTY_VIAL, missing())
        if (shops.isEmpty()) return
        val remaining = plan.deadline - System.currentTimeMillis()
        if (remaining <= 0L) return
        withTimeoutOrNull(minOf(remaining, 90_000L)) {
            if (!handler.banking.travelToBankDepositAll() || !safe()) return@withTimeoutOrNull
            handler.banking.clickBankingMode(false)
            if (!handler.banking.withdraw(Item(coins, allowance), ::safe)) return@withTimeoutOrNull

            for ((shop, _) in shops) {
                if (!safe() || missing() == 0) break
                if (handler.shop.travelToShop(shop, ::safe) == null || !handler.shop.canTradeWith(shop)) continue
                val stockId = shop.stockIdFor(EMPTY_VIAL)
                val before = bot.inventory.computeAmountForId(coins)
                var amount = minOf(missing(), shop.items.computeNonDynamicAmountForId(stockId))
                while (amount > 0) {
                    val cost = shop.computeBuyValue(stockId, amount).orElse(-1)
                    if (cost in 1..before && cost <= plan.remainingCoins && cost <= ceiling * amount &&
                        bot.inventory.hasSpaceFor(Item(stockId, amount))) {
                        if (!safe()) break
                        val purchased = handler.shop.buyQuoted(shop, stockId, amount, cost)
                        val spent = before - bot.inventory.computeAmountForId(coins)
                        if (spent > 0) plan.recordSpending(spent.toLong())
                        if (purchased || spent > 0) return@withTimeoutOrNull
                        break
                    }
                    amount--
                }
            }
        }
    }
}
