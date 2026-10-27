package game.bot.scripts.trading

import api.bot.script.DynamicBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import api.predef.ext.get
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Purchases wanted items and optional speculative stock from registered shops while the bot is at home.
 *
 * Shops are discovered directly from the global shop registry. This script deliberately contains no hardcoded list
 * of shops, NPCs, or physical shop locations.
 *
 * Every shop's bot-access predicate remains authoritative. Being at home only allows the bot to remotely open an
 * otherwise-accessible shop; it does not bypass merchant requirements or custom shop restrictions.
 *
 * This is a dynamic script because a shopping trip has no state that needs to survive logout or server restart.
 *
 * @author lare96
 */
class BuyFromStoreBotScript(bot: Bot) : DynamicBotScript(bot) {

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
     * Random purchase ordering that favors valuable items without excluding inexpensive alternatives.
     *
     * @author lare96
     */
    internal object ShopPurchaseSelection {

        /**
         * Square-root scaling keeps expensive items attractive without letting their raw GP value dominate.
         */
        fun weight(price: Double, priority: Int): Double = sqrt(price.coerceAtLeast(1.0)) * (1 + priority.coerceAtLeast(0))

        /**
         * Exponential races produce a weighted shuffle without replacement; equal weights are a uniform shuffle.
         */
        fun <T> order(items: List<T>, random: Random = Random.Default, weight: (T) -> Double): List<T> =
            items.map { it to (-ln(1.0 - random.nextDouble()) / weight(it)) }
                .sortedBy { it.second }
                .map { it.first }
    }

    override suspend fun run(): Boolean {
        // Remote shop access is a home-only convenience. Never open a registered shop from somewhere else.
        if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) {
            bot.log("Could not travel home to shop.")
            return true
        }

        // Wanted targets represent total ownership, so clear completed temporary wants before choosing purchases.
        bot.preferences.rebalanceWantedItems()

        val totalCoins = bot.itemTracker.count(COINS_ID)
        if (totalCoins < 1) {
            bot.log("I do not own any coins.")
            return true
        }

        val budget = computeBudget(totalCoins)
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
        val blockedWantedItems = HashSet<Int>()

        // Shop access depends only on the bot, so resolve it once per trip instead of once per wanted item.
        val shops = accessibleShops()

        while (remainingBudget > 0) {
            val wanted = nextWantedItem(shops, blockedWantedItems, remainingBudget) ?: break
            val spent = buyWantedItem(shops, wanted, remainingBudget)

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
        if (remainingBudget > 0) {
            spendSpeculatively(shops, remainingBudget)
        }

        bot.preferences.rebalanceWantedItems()
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
    private fun nextWantedItem(shops: List<Shop>, blocked: Set<Int>, budget: Int): WantedItemDefinition? {
        val candidates = bot.preferences.wantedItemsToAcquire()
            .filterNot { it.id in blocked || it.id == COINS_ID }
            .filter { wanted -> shopsFor(shops, wanted.id).any { canBuyOne(it, wanted.id, budget) } }
        return ShopPurchaseSelection.order(candidates) { shoppingWeight(it.id) }
            .sortedByDescending { bot.canUseWantedItem(it.id) }.firstOrNull()
    }

    /**
     * Uses current economy value and the bot's wanted priority as measures of desirability.
     */
    private fun shoppingWeight(id: Int): Double {
        val wanted = bot.preferences.getWantedItem(id) ?: WantedItemDefinition.DEFAULT.get(id).orElse(null)
        val priority = wanted?.priority?.level ?: WantedItemDefinition.DEFAULT_PRIORITY.level
        return ShopPurchaseSelection.weight(world.economy.getRealPrice(id), priority)
    }

    /**
     * Excludes unaffordable stock and items that cannot fit before rolling the purchase order.
     */
    private fun canBuyOne(shop: Shop, id: Int, budget: Int): Boolean {
        val price = shop.computeBuyValue(id, 1)
        return price.isPresent && price.asInt in 1..budget && bot.inventory.hasSpaceFor(Item(id))
    }

    /**
     * Attempts to complete one wanted-item target before moving to another wanted item.
     */
    private suspend fun buyWantedItem(shops: List<Shop>, wanted: WantedItemDefinition, budget: Int): Int {
        var spent = 0
        val failedShops = HashSet<Shop>()

        while (spent < budget) {
            val owned = bot.totalItemAmount(wanted.id)
            val amountNeeded = wanted.target - owned

            if (amountNeeded < 1) {
                break
            }

            // Stock is mutable, so re-resolve matching shops each pass rather than caching the result for the trip.
            val shop = shopsFor(shops, wanted.id)
                .filterNot { it in failedShops }
                .randomOrNull() ?: break

            if (!openShop(shop)) {
                failedShops += shop
                continue
            }

            val ownedBefore = bot.totalItemAmount(wanted.id)
            val amountSpent = spendOnItem(
                shop = shop,
                id = wanted.id,
                maxAmount = amountNeeded,
                budget = budget - spent
            )

            spent += amountSpent

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
     * Merchanting bots select merchant-only shops. Other bots select ordinary shops.
     *
     * Intelligence affects diversification:
     * - Dumb bots normally commit to one item.
     * - Average bots may buy one or several items.
     * - Intelligent bots deliberately spread their money across several different items.
     */
    private suspend fun spendSpeculatively(shops: List<Shop>, budget: Int): Int {
        val merchant = bot.preferences.likesActivity(BotActivity.MERCHANTING)

        val candidates = shops
            .filter { it.isMerchantOnly == merchant }
            .associateWith { candidate ->
                candidate.items.filterNotNull()
                    .filter { it.amount > 0 && it.id != COINS_ID && canBuyOne(candidate, it.id, budget) }
                    .map { it.id }.distinct()
            }
            .filterValues { it.isNotEmpty() }
        // Weight shops by their most desirable affordable item, so cheap-only shops do not hide coveted stock.
        val shop = ShopPurchaseSelection.order(candidates.keys.toList()) { candidate ->
            candidates.getValue(candidate).maxOf { shoppingWeight(it) }
        }.firstOrNull() ?: return 0

        if (!openShop(shop)) {
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

        val selectedItems = ShopPurchaseSelection.order(stockIds) { shoppingWeight(it) }.take(itemCount)

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
                // Let an early expensive choice buy at least one before cheaper choices consume its budget.
                budget = maxOf(itemBudget, shop.computeBuyValue(id, 1).orElse(0)).coerceAtMost(remainingBudget)
            )

            totalSpent += spent
            remainingBudget -= spent
        }

        // A selected item might not have been able to consume its slice because it was too expensive, went out of
        // stock, or could not fit. Give the remaining money another chance to be spent on the selected item set.
        var madeProgress = true

        while (remainingBudget > 0 && madeProgress) {
            madeProgress = false

            for (id in ShopPurchaseSelection.order(selectedItems) { shoppingWeight(it) }) {
                if (remainingBudget < 1) {
                    break
                }

                val spent = spendOnItem(
                    shop = shop,
                    id = id,
                    maxAmount = Int.MAX_VALUE,
                    budget = remainingBudget
                )

                if (spent > 0) {
                    totalSpent += spent
                    remainingBudget -= spent
                    madeProgress = true
                }
            }
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
        budget: Int
    ): Int {
        var remainingAmount = maxAmount
        var remainingBudget = budget
        var totalSpent = 0

        while (remainingAmount > 0 && remainingBudget > 0) {
            val stock = shop.stockAmount(id)
            if (stock < 1) {
                break
            }

            // The 317/377 shop interface gives us Buy 1, Buy 5, and Buy 10. Use the largest supported click whose
            // current dynamic price stays inside the remaining budget.
            val amount = purchaseAmount(
                shop = shop,
                id = id,
                maxAmount = minOf(stock, remainingAmount),
                budget = remainingBudget
            ) ?: break

            val coinsBefore = bot.inventory.computeAmountForId(COINS_ID)
            val itemsBefore = bot.inventory.computeAmountForId(id)

            val bought = when (amount) {
                10 -> handler.shop.buy10(id).await()
                5 -> handler.shop.buy5(id).await()
                1 -> handler.shop.buy1(id).await()
                else -> error("Unsupported shop purchase amount: $amount")
            }

            val coinsAfter = bot.inventory.computeAmountForId(COINS_ID)
            val itemsAfter = bot.inventory.computeAmountForId(id)

            val acquired = itemsAfter - itemsBefore
            val spent = coinsBefore - coinsAfter

            // The shop handler already waits for an inventory increase. Keep these checks anyway so a future handler
            // change, stale stock, or failed transaction can never create a tight purchase loop.
            if (!bought || acquired < 1 || spent < 1) {
                break
            }

            totalSpent += spent
            remainingBudget -= spent
            remainingAmount -= acquired
        }

        return totalSpent
    }

    /**
     * Selects the largest supported shop purchase whose exact dynamic price fits inside [budget].
     */
    private fun purchaseAmount(
        shop: Shop,
        id: Int,
        maxAmount: Int,
        budget: Int
    ): Int? {
        for (amount in intArrayOf(10, 5, 1)) {
            if (amount > maxAmount) {
                continue
            }

            val price = shop.computeBuyValue(id, amount).orElse(Int.MAX_VALUE)
            if (price <= budget) {
                return amount
            }
        }

        return null
    }

    /**
     * Returns every registered GP shop this specific bot is currently authorized to use.
     */
    private fun accessibleShops(): List<Shop> {
        return world.shops.values()
            .filter { it.currency == Currency.COINS }
            .filter { it.botAccess.apply(bot) }
    }

    /**
     * Returns the shops in [shops] that currently contain positive stock for [id].
     */
    private fun shopsFor(shops: List<Shop>, id: Int): List<Shop> {
        return shops.filter { it.stockAmount(id) > 0 }
    }

    /**
     * Opens [shop] remotely while at home.
     *
     * Access is checked again immediately before opening so candidate selection can never accidentally bypass a
     * merchant requirement or other custom bot-access predicate.
     */
    private fun openShop(shop: Shop): Boolean {
        if (SubZone.HOME !in bot.subZones) {
            return false
        }

        if (!shop.botAccess.apply(bot)) {
            return false
        }

        bot.overlays.open(ShopInterface(shop))

        // Verify the expected interface actually became the active shop overlay before sending shop clicks.
        return bot.overlays[ShopInterface::class]?.shop === shop
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
