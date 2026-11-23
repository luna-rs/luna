package api.bot.action

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotSupplyPurchase.Quote
import api.bot.action.trading.BotFoodDemand
import api.bot.action.trading.BotTradeIntentions
import api.bot.script.BotScript
import api.bot.zone.ProductionZones
import api.bot.zone.SubZone
import api.predef.ext.totalItemAmount
import engine.bot.coordinator.skill.FishingScriptFactory
import game.bot.scripts.skills.CookFoodBotScript
import game.bot.scripts.skills.RestockFoodSupplyScript
import game.bot.scripts.trading.TradeBotScript
import game.player.item.consume.food.Food
import game.skill.cooking.cookFood.CookSource
import game.skill.cooking.cookFood.Food as RawFood
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Restocks an active personal food reserve through ordinary purchases or owned ingredients.
 *
 * @author lare96
 */
internal class FoodSupplyAcquisition(private val bot: Bot, private val handler: BotActionHandler) {
    private val purchase = BotSupplyPurchase(bot, handler)

    companion object {
        const val TARGET = 100
        private const val LOW_RESERVE = 16
        private val FOOD_WAIT = 40.minutes
        private val RETRY = 5.minutes
        private var Bot.shortageSince by Attr.long().persist("pk_foodless_since")
        private var Bot.nextFallbackAt by Attr.long().persist("pk_next_food_cooking_at")
        private var Bot.managesFoodReserve by Attr.boolean().persist("manages_food_reserve")
        private var Bot.nextFoodRestockAt by Attr.long().persist("next_food_restock_at")

        internal fun mayCookAfterWaiting(since: Long, nextAttempt: Long, now: Long): Boolean =
            since > 0 && now >= since && now - since >= FOOD_WAIT.inWholeMilliseconds && now >= nextAttempt

        internal fun stock(bot: Bot): Int =
            (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull())
                .sumOf { if ((Food.ID_TO_FOOD[it.unnotedItemDef.id()]?.heal ?: 0) > 0) it.amount.toLong() else 0L }
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        internal fun retainedReserve(bot: Bot): Int = if (bot.managesFoodReserve) TARGET else 0

        @JvmStatic
        fun idleScript(bot: Bot): RestockFoodSupplyScript? {
            if ((!bot.managesFoodReserve && !BotFoodDemand.active(bot)) ||
                System.currentTimeMillis() < bot.nextFoodRestockAt || stock(bot) >= LOW_RESERVE ||
                !BotSupplyPurchase(bot, bot.actionHandler).safe()) return null
            return RestockFoodSupplyScript(bot)
        }
    }

    suspend fun obtainForPatrol(allowed: () -> Boolean): Boolean {
        if (stock(bot) > 0) return boughtFood()
        recordShortage()
        if (withTimeoutOrNull(2.minutes) { buyCooked(allowed, 8) } == true || stock(bot) > 0) return true
        if (allowed() && purchase.safe() && fallbackReady()) {
            bot.scriptStack.pushHead(RestockFoodSupplyScript(bot, cookedChecked = true))
        }
        return false
    }

    suspend fun restock(allowed: () -> Boolean, cookedChecked: Boolean): BotScript? {
        if (!allowed() || !purchase.safe() || stock(bot) >= LOW_RESERVE) return null
        recordShortage()
        bot.nextFoodRestockAt = System.currentTimeMillis() + RETRY.inWholeMilliseconds
        if (fallbackDue() && System.currentTimeMillis() < bot.nextFallbackAt) {
            cookOwned(allowed)?.let { return it }
        }
        if (!cookedChecked && withTimeoutOrNull(3.minutes) { buyCooked(allowed, TARGET) } == true) {
            if (allowed() && purchase.safe()) handler.banking.travelToBankDepositAll()
            return null
        }
        if (!allowed() || !purchase.safe() || stock(bot) >= LOW_RESERVE) return null
        if (fallbackDue()) cookOwned(allowed)?.let { return it }
        if (!fallbackReady()) return null
        bot.nextFallbackAt = System.currentTimeMillis() + RETRY.inWholeMilliseconds
        return withTimeoutOrNull(3.minutes) { cookOrBuyIngredients(allowed) }
            ?: if (allowed() && purchase.safe() && budget() == 0L)
                FishingScriptFactory.forFoodReserve(bot, TARGET - stock(bot)) else null
    }

    fun observeCookedStock() {
        if (stock(bot) > 0) boughtFood()
    }

    suspend fun cookOwned(allowed: () -> Boolean, attempted: Set<RawFood> = emptySet()): CookFoodBotScript? {
        val recipe = recipes().filter { it !in attempted && bot.totalItemAmount(it.raw) > 0 }
            .maxByOrNull { Food.ID_TO_FOOD.getValue(it.cooked).heal } ?: return null
        return cookIngredients(recipe, TARGET - stock(bot), allowed)
    }

    private suspend fun cookIngredients(recipe: RawFood, limit: Int, allowed: () -> Boolean): CookFoodBotScript? {
        if (limit <= 0) return null
        if (!allowed() || !purchase.safe() || !handler.banking.travelToBankDepositAll() ||
            !allowed() || !purchase.safe() || recipe !in recipes()) return null
        val amount = minOf(limit, bot.bank.computeAmountForId(recipe.raw))
        if (amount <= 0) return null
        val zones = (ProductionZones.RANGES + SubZone.VARROCK_EAST_BANK + SubZone.AL_KHARID_BANK).toMutableList()
        return CookFoodBotScript(bot, recipe, 20.minutes, zones, amount)
    }

    /**
     * Refreshes a genuine demand even when the market's shorter purchasing window expires mid-shortage.
     */
    private fun recordShortage() {
        bot.managesFoodReserve = true
        val now = System.currentTimeMillis()
        if (bot.shortageSince <= 0 || bot.shortageSince > now) bot.shortageSince = now
        BotTradeIntentions.requestFood(bot, LOW_RESERVE, minimumHeal = 1)
    }

    private fun fallbackDue(): Boolean = budget() == 0L ||
        mayCookAfterWaiting(bot.shortageSince, 0L, System.currentTimeMillis())

    private fun fallbackReady(): Boolean = System.currentTimeMillis() >= bot.nextFallbackAt && fallbackDue()

    private fun recipes(): List<RawFood> {
        val skill = bot.skill(Skill.COOKING)
        return RawFood.entries.filter { it.lvl <= minOf(skill.staticLevel, skill.level) &&
            it.source != CookSource.FIRE_ONLY && (Food.ID_TO_FOOD[it.cooked]?.heal ?: 0) > 0 &&
            BotTradeIntentions.productionLimit(bot, it.raw) == null }
    }

    private suspend fun cookOrBuyIngredients(allowed: () -> Boolean): CookFoodBotScript? {
        if (!allowed() || !purchase.safe()) return null
        val opening = stock(bot)
        val candidates = recipes().associateBy { it.raw }
        for (choice in quotes(candidates.keys, cooked = false)) {
            if (!allowed() || !purchase.safe() || stock(bot) != opening) break
            val recipe = candidates.getValue(choice.item.id)
            val amount = TARGET - opening
            val lot = largestLot(choice, amount, Long.MAX_VALUE) ?: continue
            val before = bot.totalItemAmount(recipe.raw)
            purchase.buy(lot) { allowed() && stock(bot) == opening && recipe in recipes() }
            val bought = (bot.totalItemAmount(recipe.raw) - before).coerceAtMost(lot.item.amount)
            if (bought > 0) return cookIngredients(recipe, bought, allowed)
        }
        return null
    }

    private suspend fun buyCooked(allowed: () -> Boolean, limit: Int): Boolean {
        val opening = stock(bot)
        for (choice in quotes(BotFoodDemand.alternatives(bot), cooked = true)) {
            if (!allowed() || !purchase.safe()) break
            val maximum = minOf(limit, TARGET - stock(bot))
            val lot = largestLot(choice, maximum, BotFoodDemand.priceCeiling(bot, choice.item.id)) ?: continue
            purchase.buy(lot, allowed)
            if (stock(bot) > opening) return boughtFood()
        }
        return stock(bot) > opening && boughtFood()
    }

    private fun boughtFood(): Boolean {
        bot.shortageSince = 0L
        bot.nextFallbackAt = 0L
        return true
    }

    private fun budget(): Long = minOf(purchase.coins(), BotFoodDemand.allowance(bot).toLong())

    private fun quotes(ids: Set<Int>, cooked: Boolean): List<Quote> {
        val funds = budget()
        if (funds == 0L) return emptyList()
        val offers = TradeBotScript.carriedSupply(bot, ids).groupBy { it.id }
        return ids.asSequence().flatMap { id ->
            listOfNotNull(purchase.shopQuote(id, 1), purchase.directQuote(id, 1, offers[id].orEmpty())).asSequence()
        }.filter { it.coins in 1..funds &&
            (!cooked || it.unitPrice <= BotFoodDemand.priceCeiling(bot, it.item.id)) }
            .sortedWith(compareBy<Quote> { it.coins }.thenByDescending { it.direct })
            .take(4).toList()
    }

    private fun largestLot(choice: Quote, limit: Int, ceiling: Long): Quote? {
        val funds = budget()
        var low = 1
        var high = minOf(limit.toLong(), funds / choice.unitPrice).toInt()
        var best: Quote? = null
        while (low <= high) {
            val quantity = (low + high) / 2
            val id = choice.item.id
            val quote = if (choice.direct) purchase.directQuote(id, quantity) else purchase.shopQuote(id, quantity)
            if (quote != null && quote.coins <= funds && quote.unitPrice <= ceiling) {
                best = quote
                low = quantity + 1
            } else high = quantity - 1
        }
        return best
    }
}
