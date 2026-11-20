package game.bot.scripts.trading

import api.bot.action.stockIdFor
import api.bot.action.trading.BotShopResale
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotMerchantProceeds
import api.bot.action.trading.BotMerchantCapital
import api.bot.action.trading.BotFoodDemand
import api.bot.action.trading.BotTradeSelection
import api.bot.script.DynamicBotScript
import api.predef.*
import api.predef.ext.*
import api.predef.ext.get
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/**
 * Purchases wanted items and optional speculative stock by visiting the shops' native NPCs.
 *
 * This is a dynamic script because a shopping trip has no state that needs to survive logout or server restart.
 *
 * @author lare96
 */
class BuyFromStoreBotScript(bot: Bot, private val shoppingBudget: Int? = null) : DynamicBotScript(bot) {

    /**
     * Keeps one ordinary shopping visit within its first generated food policy and original food allowance.
     *
     * @author lare96
     */
    private class FoodBudget(val identity: String, var remainingCoins: Int)

    /**
     * Retains a generated candidate's policy and allowance before opening a shop or attempting any purchase.
     *
     * @author lare96
     */
    private class WantedPurchase(val wanted: WantedItemDefinition, val foodIdentity: String?, val foodCoins: Int)

    private var foodBudget: FoodBudget? = null

    companion object {

        /**
         * The currency used by this shopping behavior.
         *
         * Other shop currencies are intentionally ignored because the shopping budget is defined as a percentage of
         * the bot's current GP wealth.
         */
        private val COINS_ID = Currency.COINS.id

        private val ORDINARY_BUDGET = 0.01..0.04
        private val MERCHANT_BUDGET = 0.07..0.10
        private val STRONG_MERCHANT_BUDGET = 0.12..0.15
    }

    /**
     * Random purchase ordering that favours established supplies and explicit acquisition priorities.
     *
     * @author lare96
     */
    internal object ShopPurchaseSelection {

        /**
         * Square-root scaling softens observed popularity without making expensive niche items more desirable.
         */
        fun weight(popularity: Double, priority: Int): Double =
            sqrt(1.0 + popularity.coerceAtLeast(0.0)) * (1 + priority.coerceAtLeast(0))

        /**
         * Exponential races produce a weighted shuffle without replacement; equal weights are a uniform shuffle.
         */
        fun <T> order(items: List<T>, random: Random = Random.Default, weight: (T) -> Double): List<T> =
            items.map { it to (-ln(1.0 - random.nextDouble()) / weight(it)) }
                .sortedBy { it.second }
                .map { it.first }
    }

    override suspend fun run(): Boolean {
        // Wanted targets represent total ownership, so clear completed temporary wants before choosing purchases.
        bot.preferences.rebalanceWantedItems()

        val totalCoins = bot.itemTracker.count(COINS_ID)
        if (totalCoins < 1) {
            bot.log("I do not own any coins.")
            return true
        }

        val budget = (shoppingBudget ?: computeBudget(totalCoins)).coerceIn(0, totalCoins)
        if (budget == 0) return true
        val shops = accessibleShops()
        if (!bot.preferences.isMerchant && BotTradeIntentions.purchases(bot).none { wanted ->
                shops.any { shop -> shop.computeBuyValue(shop.stockIdFor(wanted.id), 1).let {
                    it.isPresent && it.asInt in 1..budget
                } }
            }) return true

        if (!prepareInventory(budget)) {
            bot.log("Could not prepare inventory for shopping.")
            return true
        }

        if (bot.inventory.isFull) {
            if (handler.banking.travelToBankDepositAll()) {
                bot.log("Clearing full inventory and preparing.")
                prepareInventory(budget)
                return false
            } else {
                bot.log("Could not clear full inventory.")
                stop()
                return true
            }
        }

        // Budget is based on total GP wealth, but shops can only spend coins physically carried in the inventory.
        var remainingBudget = minOf(budget, bot.inventory.computeAmountForId(COINS_ID))
        if (remainingBudget < 1) {
            return true
        }

        // Wanted items always have first priority.
        //
        // One wanted item is pushed toward its target before another one is selected. If an item cannot currently be
        // purchased because every accessible shop is empty, too expensive, or inaccessible, block it for this trip.
        val shopPurchases = mutableListOf<BotShopResale.Batch>()
        val blockedWantedItems = HashSet<Int>()

        while (remainingBudget > 0) {
            val purchase = nextWantedItem(shops, blockedWantedItems, remainingBudget) ?: break
            val wanted = purchase.wanted
            val spent = buyWantedItem(shops, wanted, remainingBudget, purchase.foodIdentity, purchase.foodCoins, shopPurchases)

            if (spent < 1) {
                // Avoid selecting an impossible wanted item forever during this shopping trip.
                blockedWantedItems += wanted.id
                continue
            }

            remainingBudget -= spent
            bot.preferences.rebalanceWantedItems()

            if (bot.totalItemAmount(wanted.id) < wanted.target) {
                // Something prevented us from finishing it. Other wanted items can still be attempted this trip.
                blockedWantedItems += wanted.id
            }
        }

        // Only start random shopping once no currently-purchasable wanted item remains.
        if (remainingBudget > 0 && bot.preferences.isMerchant) {
            spendSpeculatively(shops.filterNot { it.isGeneralStore }, remainingBudget, shopPurchases)
        }

        bot.preferences.rebalanceWantedItems()
        ShopPurchaseOffers.queue(bot, shopPurchases)
        return true
    }

    /**
     * Computes how much of the bot's total GP may be used during this shopping trip.
     */
    private fun computeBudget(totalCoins: Int): Int {
        val percentage = when {
            bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> {
                rand(STRONG_MERCHANT_BUDGET)
            }

            bot.preferences.likesActivity(BotActivity.MERCHANTING) -> {
                rand(MERCHANT_BUDGET)
            }

            else -> rand(ORDINARY_BUDGET)
        }

        // Allow an extremely poor bot to spend its final coin rather than rounding its budget down to zero.
        return (totalCoins.toDouble() * percentage).toInt().coerceAtLeast(1)
    }

    /**
     * Frees inventory space and makes the shopping budget available as carried coins when possible.
     */
    private suspend fun prepareInventory(budget: Int): Boolean {
        var carriedCoins = bot.inventory.computeAmountForId(COINS_ID)
        val hasNonCoins = bot.inventory.any { it != null && it.id != COINS_ID }

        // Avoid a needless banking trip when the bot already has clean inventory space and enough carried GP.
        if (!hasNonCoins && carriedCoins >= budget) {
            return true
        }

        if (!handler.banking.travelToBankOpen()) {
            // Failing to bank does not necessarily kill the trip. We can still shop with whatever GP is carried.
            return carriedCoins > 0
        }

        if (hasNonCoins) {
            // Keep coins carried while clearing space for potentially non-stackable purchases.
            handler.banking.depositInventory(setOf(COINS_ID))
        }

        carriedCoins = bot.inventory.computeAmountForId(COINS_ID)

        val missingCoins = (budget - carriedCoins).coerceAtLeast(0)
        if (missingCoins > 0) {
            // The banking handler already clamps withdrawals when the bank contains less than the requested amount.
            handler.banking.withdraw(Item(COINS_ID, missingCoins))
        }

        return bot.inventory.computeAmountForId(COINS_ID) > 0
    }

    /**
     * Returns the next wanted item that still needs ownership and is stocked by an accessible shop.
     */
    private fun nextWantedItem(shops: List<Shop>, blocked: Set<Int>, budget: Int): WantedPurchase? {
        val candidates = BotTradeIntentions.purchases(bot)
            .filterNot { it.id in blocked || it.id == COINS_ID }
            .filter { wanted -> shopsFor(shops, wanted.id).any { canBuyOne(it, wanted.id, budget) } }
        val ordered = ShopPurchaseSelection.order(candidates) { shoppingWeight(it.id) }
        return BotTradeSelection.supplyOrder(ordered) { BotTradeSelection.supplyPriority(it.id) }
            .sortedWith(compareByDescending<WantedItemDefinition> {
                it.priority == WantedItemDefinition.WantedItemPriority.URGENT
            }
                .thenByDescending { bot.canUseWantedItem(it.id) }).firstOrNull()?.let { wanted ->
                val identity = BotFoodDemand.identity(bot)?.takeIf { BotFoodDemand.handles(bot, wanted.id) }
                WantedPurchase(wanted, identity, if (identity == null) 0 else BotFoodDemand.allowance(bot))
            }
    }

    /**
     * Uses observed popularity and the bot's wanted priority as measures of desirability.
     */
    private fun shoppingWeight(id: Int): Double {
        val wanted = bot.preferences.getWantedItem(id) ?: WantedItemDefinition.DEFAULT.get(id).orElse(null)
        val priority = wanted?.priority?.level ?: WantedItemDefinition.DEFAULT_PRIORITY.level
        return ShopPurchaseSelection.weight(BotTradeSelection.popularity(id), priority)
    }

    /**
     * Excludes unaffordable stock and items that cannot fit before rolling the purchase order.
     */
    private fun canBuyOne(shop: Shop, id: Int, budget: Int): Boolean {
        val stockId = shop.stockIdFor(id)
        val price = shop.computeBuyValue(stockId, 1)
        return price.isPresent && price.asInt in 1..budget && bot.inventory.hasSpaceFor(Item(stockId))
    }

    /**
     * Attempts to complete one wanted-item target before moving to another wanted item.
     */
    private suspend fun buyWantedItem(shops: List<Shop>, wanted: WantedItemDefinition, budget: Int,
                                      foodIdentity: String?, foodCoins: Int,
                                      purchases: MutableList<BotShopResale.Batch>): Int {
        val production = BotTradeIntentions.productionLimit(bot, wanted.id)
        val food = if (foodIdentity == null) null else {
            val previous = foodBudget
            if (previous != null && previous.identity != foodIdentity) return 0
            previous ?: FoodBudget(foodIdentity, foodCoins.coerceAtLeast(0)).also { foodBudget = it }
        }
        var spent = 0
        val failedShops = HashSet<Shop>()

        while (spent < budget) {
            if (food != null && (food.identity != BotFoodDemand.identity(bot) || !BotFoodDemand.handles(bot, wanted.id) ||
                    food.remainingCoins < 1)) break
            val owned = bot.totalItemAmount(wanted.id)
            val amountNeeded = minOf(wanted.target - owned,
                if (food == null) Int.MAX_VALUE else BotFoodDemand.deficit(bot))

            if (amountNeeded < 1) {
                break
            }

            // Stock is mutable, so re-resolve matching shops each pass rather than caching the result for the trip.
            val shop = shopsFor(shops, wanted.id)
                .filterNot { it in failedShops }
                .randomOrNull() ?: break

            if (handler.shop.travelToShop(shop) { isActionSafe() } == null) {
                failedShops += shop
                continue
            }

            val ownedBefore = bot.totalItemAmount(wanted.id)
            val amountSpent = spendOnItem(
                shop = shop,
                id = wanted.id,
                maxAmount = amountNeeded,
                budget = minOf(budget - spent, food?.remainingCoins ?: Int.MAX_VALUE),
                expectedProduction = production,
                expectedFoodIdentity = food?.identity,
                purchases = purchases
            )

            spent += amountSpent
            if (food != null) food.remainingCoins = (food.remainingCoins - amountSpent).coerceAtLeast(0)

            // If this shop produced no ownership increase, trying it repeatedly would create an infinite loop.
            if (bot.totalItemAmount(wanted.id) <= ownedBefore) {
                failedShops += shop
            }
        }

        return spent
    }

    /**
     * Spends leftover money after wanted items have been handled.
     *
     * Intelligence affects diversification:
     * - Dumb bots normally commit to one item.
     * - Average bots may buy one or several items.
     * - Intelligent bots deliberately spread their money across several different items.
     */
    private suspend fun spendSpeculatively(shops: List<Shop>, budget: Int,
                                           purchases: MutableList<BotShopResale.Batch>): Int {
        if (!BotMerchantCapital.active(bot)) return 0
        val capital = BotMerchantCapital.snapshot(bot)
        if ((capital.cashRecovery || capital.electiveCoins < 1L) && capital.stockingCoins < 1L) return 0

        val candidates = shops
            .associateWith { candidate ->
                candidate.items.filterNotNull()
                    .filter { it.amount > 0 && !it.isDynamic && it.id != COINS_ID }
                    .map { it.unnotedItemDef.id() }.distinct().filter {
                        BotShopResale.remainingStock(bot, it) > 0 && canBuyOne(candidate, it, budget)
                    }
            }
            .filterValues { it.isNotEmpty() }
        // Choose the desired affordable stock before its shop, retaining the supply category decision below.
        val stock = candidates.flatMap { (shop, ids) -> ids.map { shop to it } }
        val opening = BotTradeSelection.supplyOrder(
            ShopPurchaseSelection.order(stock) { shoppingWeight(it.second) }) {
            BotTradeSelection.supplyPriority(it.second)
        }.firstOrNull() ?: return 0
        val shop = opening.first

        if (handler.shop.travelToShop(shop) { isActionSafe() } == null) {
            return 0
        }

        val stockIds = candidates.getValue(shop)

        if (stockIds.isEmpty()) {
            return 0
        }

        val itemCount = when {
            bot.personality.isDumb -> 1

            bot.personality.isIntelligent -> {
                // Smart bots intentionally diversify over several different shop items.
                rand(3, 5).coerceAtMost(stockIds.size)
            }

            rand(bot.personality.intelligence) -> {
                // Average bots become more likely to diversify as their raw intelligence approaches the smart range.
                rand(2, 3).coerceAtMost(stockIds.size)
            }

            else -> 1
        }

        val remaining = ShopPurchaseSelection.order(stockIds.filter { it != opening.second }) { shoppingWeight(it) }
        val selectedItems = (listOf(opening.second) + BotTradeSelection.supplyOrder(remaining) {
            BotTradeSelection.supplyPriority(it)
        }).take(itemCount)

        var remainingBudget = budget
        var totalSpent = 0

        // First split the budget between selected items. Without this pass, even an intelligent bot could accidentally
        // spend its entire budget on the first item and never demonstrate the intended diversification behavior.
        selectedItems.forEachIndexed { index, id ->
            if (remainingBudget < 1) {
                return@forEachIndexed
            }

            val itemsRemaining = selectedItems.size - index

            val itemBudget = if (itemsRemaining == 1) {
                remainingBudget
            } else {
                (remainingBudget / itemsRemaining).coerceAtLeast(1)
            }

            val spent = spendOnItem(
                shop = shop,
                id = id,
                maxAmount = Int.MAX_VALUE,
                speculative = true,
                purchases = purchases,
                // Let an early expensive choice buy at least one before cheaper choices consume its budget.
                budget = maxOf(itemBudget, shop.computeBuyValue(shop.stockIdFor(id), 1).orElse(0))
                    .coerceAtMost(remainingBudget)
            )

            totalSpent += spent
            remainingBudget -= spent
        }

        return totalSpent
    }

    /**
     * Purchases up to [maxAmount] of one item without spending more than [budget].
     */
    private suspend fun spendOnItem(
        shop: Shop,
        id: Int,
        maxAmount: Int,
        budget: Int,
        expectedProduction: BotTradeIntentions.ProductionPurchaseLimit? = null,
        expectedFoodIdentity: String? = null,
        speculative: Boolean = false,
        purchases: MutableList<BotShopResale.Batch>? = null
    ): Int {
        val production = expectedProduction ?: BotTradeIntentions.productionLimit(bot, id)
        var remainingAmount = maxAmount
        var remainingBudget = budget
        var totalSpent = 0

        while (remainingAmount > 0 && remainingBudget > 0) {
            val stockId = shop.stockIdFor(id)
            val stock = shop.stockAmount(stockId)
            if (stock < 1) {
                break
            }

            val coinsBefore = bot.inventory.computeAmountForId(COINS_ID)
            val itemsBefore = bot.totalItemAmount(id)

            val bought = if (speculative) {
                val quote = BotMerchantProceeds.shopPurchase(bot, shop, stockId, remainingAmount, remainingBudget) ?: break
                handler.shop.buyQuoted(shop, stockId, quote.quantity, quote.coins)
            } else handler.shop.buyUpTo(shop, id, remainingAmount, remainingBudget, production, expectedFoodIdentity)

            val coinsAfter = bot.inventory.computeAmountForId(COINS_ID)
            val itemsAfter = bot.totalItemAmount(id)

            val acquired = itemsAfter - itemsBefore
            val spent = coinsBefore - coinsAfter

            // Verify actual progress so a failed transaction cannot create a tight purchase loop.
            if (!bought || acquired < 1 || spent < 1) {
                break
            }

            if (bot.preferences.isMerchant && production == null && expectedFoodIdentity == null)
                purchases?.add(BotShopResale.Batch(id, acquired, spent))
            totalSpent += spent
            remainingBudget -= spent
            remainingAmount -= acquired
            if (speculative) break
        }

        return totalSpent
    }

    /**
     * Returns every registered GP shop this specific bot is currently authorized to use.
     */
    private fun accessibleShops(): List<Shop> {
        return world.shops.values()
            .filter { it.currency == Currency.COINS && it.npcSellers.isNotEmpty() }
    }

    /**
     * Returns the shops in [shops] that currently contain positive stock for [id].
     */
    private fun shopsFor(shops: List<Shop>, id: Int): List<Shop> {
        return shops.filter { it.stockAmount(it.stockIdFor(id)) > 0 }
    }

    /**
     * Returns the amount of [id] that this shop currently has available to purchase.
     */
    private fun Shop.stockAmount(id: Int): Int {
        val index = items.computeIndexForId(id)
        if (index == -1) {
            return 0
        }

        return items[index]?.amount?.coerceAtLeast(0) ?: 0
    }
}
