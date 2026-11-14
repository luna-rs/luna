package api.bot.action

import api.bot.SuspendableCondition
import api.bot.Suspendable.delay
import api.bot.SuspendableFuture
import api.bot.SuspendableFuture.SuspendableFutureFailed
import api.bot.action.trading.BotTradeIntentions
import api.bot.zone.SubZone
import api.bot.script.BotScript.ActionSafety
import api.bot.script.BotScript
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.mob.bot.Bot
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.EntityState
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.DialogueQueue
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import java.util.WeakHashMap

/**
 * A [BotActionHandler] implementation for shop related actions.
 */
class BotShopActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /** One market preview per missing item before the owning script falls back to a shop. */
    private val marketAttempts = WeakHashMap<BotScript, MutableSet<Int>>()

    /**
     * One finite GP budget shared by every item in a guarded acquisition basket.
     *
     * @author lare96
     */
    private class PurchaseBudget(var remainingCoins: Int, val maximumUnitPrices: Map<Int, Long>,
                                 val recordSpending: (Int) -> Unit,
                                 val productionLimits: Map<Int, BotTradeIntentions.ProductionPurchaseLimit>)

    /**
     * Executes one price-protected GP purchase on the game thread through the native shop transaction.
     * The expected overlay, access, quantity, space and current quote are checked without suspending before buying.
     * Ordinary queued shop clicks retain their existing behavior; this action is for finite bargain purchases.
     */
    fun buyQuoted(shop: Shop, id: Int, amount: Int, maxTotalCost: Int,
                  expectedProduction: BotTradeIntentions.ProductionPurchaseLimit? = null): Boolean =
        shop.currency == Currency.COINS && buyExact(shop, id, amount, maxTotalCost, expectedProduction)

    /**
     * Buys the largest affordable batch of up to one hundred within the caller's quantity and spending limits.
     * Stock, capacity and the exact price are checked in the same game-thread operation as payment, so restocking
     * cannot turn a partial batch into an oversized purchase. Successful purchases yield before the next batch.
     */
    suspend fun buyUpTo(shop: Shop, id: Int, maxAmount: Int, maxTotalCost: Int,
                        expectedProduction: BotTradeIntentions.ProductionPurchaseLimit? = null): Boolean {
        val production = BotTradeIntentions.productionLimit(bot, id)
        if (expectedProduction != null && (production == null ||
            production.identity !== expectedProduction.identity || production.expires != expectedProduction.expires)) return false
        val wallet = minOf(maxTotalCost.toLong(), bot.inventory.computeAmountForId(shop.currency.id).toLong(),
            production?.maximumCoins ?: Long.MAX_VALUE).toInt()
        if (maxAmount < 1 || wallet < 1) return false
        if (production != null && shop.currency != Currency.COINS) return false
        val capacity = if (itemDef(id).isStackable) Int.MAX_VALUE else bot.inventory.computeRemainingSize()
        var quantity = minOf(100, maxAmount, shop.items.computeAmountForId(id), capacity,
            Int.MAX_VALUE - bot.inventory.computeAmountForId(id))
        if (production != null) quantity = minOf(quantity,
            (production.target.toLong() - bot.totalItemAmount(id)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
        while (quantity > 0) {
            val quote = shop.computeBuyValue(id, quantity).orElse(-1)
            if (quote in 1..wallet && bot.inventory.hasSpaceFor(Item(id, quantity)) &&
                (production == null || (quote.toLong() + quantity - 1L) / quantity <= production.maximumUnitPrice)) {
                val bought = buyExact(shop, id, quantity, quote, expectedProduction ?: production)
                if (bought) delay(1.seconds)
                return bought
            }
            quantity--
        }
        return false
    }

    /**
     * Applies an exact quantity and currency ceiling to one native shop transaction without suspending.
     */
    private fun buyExact(shop: Shop, id: Int, amount: Int, maxTotalCost: Int,
                         expectedProduction: BotTradeIntentions.ProductionPurchaseLimit? = null): Boolean {
        if (amount !in 1..100 || maxTotalCost < 1 || !ActionSafety.NON_COMBAT.allows(bot) ||
            bot.isConnectedPlayer && !bot.isAutomationActive ||
            !shop.botAccess.apply(bot) ||
            bot.overlays[ShopInterface::class]?.shop !== shop) return false
        val index = shop.items.computeIndexForId(id)
        if (index < 0) return false
        val item = shop.items[index] ?: return false
        if (item.id != id || item.isDynamic || item.amount < amount ||
            bot.inventory.computeAmountForId(id).toLong() + amount > Int.MAX_VALUE ||
            !bot.inventory.hasSpaceFor(Item(id, amount))) return false
        val quoted = shop.computeBuyValue(id, amount).orElse(-1)
        val currency = shop.currency.id
        val coins = bot.inventory.computeAmountForId(currency)
        if (quoted !in 1..maxTotalCost || coins < quoted) return false
        val production = BotTradeIntentions.productionLimit(bot, id)
        if (expectedProduction != null && (production == null ||
            production.identity !== expectedProduction.identity || production.expires != expectedProduction.expires)) return false
        if (production != null && (currency != Currency.COINS.id ||
            bot.totalItemAmount(id).toLong() + amount > production.target || quoted.toLong() > production.maximumCoins ||
            (quoted.toLong() + amount - 1L) / amount > production.maximumUnitPrice)) return false
        val before = bot.inventory.computeAmountForId(id)
        val bought = shop.buy(bot, index, amount)
        val spent = (coins - bot.inventory.computeAmountForId(currency)).coerceAtLeast(0)
        if (spent > 0 && production != null)
            BotTradeIntentions.recordProductionPurchase(bot, id, spent.toLong(), production.identity)
        return bought && bot.inventory.computeAmountForId(id) == before + amount && spent == quoted
    }

    /**
     * Attempts to purchase 1 of [id] from the currently open shop.
     */
    fun buy1(id: Int) = buy(id, 1)

    /**
     * Attempts to purchase 5 of [id] from the currently open shop.
     */
    fun buy5(id: Int) = buy(id, 5)

    /**
     * Attempts to purchase 10 of [id] from the currently open shop.
     */
    fun buy10(id: Int) = buy(id, 10)

    /**
     * Attempts to purchase 100 of [id] from the currently open shop.
     */
    fun buy100(id: Int) = buy(id, 100)

    /**
     * Attempts to sell 1 of [id] to the currently open shop.
     */
    fun sell1(id: Int) = sell(id, 1)

    /**
     * Attempts to sell 5 of [id] to the currently open shop.
     */
    fun sell5(id: Int) = sell(id, 5)

    /**
     * Attempts to sell 10 of [id] to the currently open shop.
     */
    fun sell10(id: Int) = sell(id, 10)

    /**
     * Attempts to sell up to 100 of [id] to the currently open shop.
     */
    fun sell100(id: Int) = sell(id, 100)

    /**
     * Attempts to purchase either 1, 5, 10, or 100 of an item.
     */
    private fun buy(id: Int, amount: Int): SuspendableFuture {
        // Check if a shop is open.
        val shopInterface = bot.overlays[ShopInterface::class] ?: return SuspendableFutureFailed

        bot.log("Buying $amount of ${itemName(id)}.")
        val shopIndex = shopInterface.shop.items.computeIndexForId(id)
        if (shopIndex == -1) {
            // Shop doesn't have the item.
            bot.log("Shop doesn't have that item.")
            return SuspendableFutureFailed
        }
        val shopItem = shopInterface.shop.items[shopIndex]
        if (shopItem == null || shopItem.amount < 1) {
            bot.log("Shop doesn't have that item in stock.")
            return SuspendableFutureFailed
        }

        // Only require enough room for one item here.
        //
        // Shop.buy already clamps bulk purchases to the amount that actually fits in the inventory. Checking against the entire
        // shop stack would incorrectly reject something like Buy 1 on a non-stackable item when the shop currently stocks 50.
        if (!bot.inventory.hasSpaceFor(shopItem.withAmount(1))) {
            bot.log("Not enough inventory space to buy this item.")
            return SuspendableFutureFailed
        }

        val amountBefore = bot.inventory.computeAmountForId(id)
        val boughtItemCond = SuspendableCondition { bot.inventory.computeAmountForId(id) > amountBefore }
        when (amount) {
            1 -> bot.output.sendItemWidgetClick(2, shopIndex, 3900, id)
            5 -> bot.output.sendItemWidgetClick(3, shopIndex, 3900, id)
            10 -> bot.output.sendItemWidgetClick(4, shopIndex, 3900, id)
            100 -> bot.output.sendItemWidgetClick(5, shopIndex, 3900, id)
            else -> throw IllegalStateException("Invalid amount.")
        }
        return boughtItemCond.submit(5) // Unsuspend when the inventory amount increases.
    }

    /**
     * Attempts to sell either 1, 5, 10, or 100 of an item.
     */
    private fun sell(id: Int, amount: Int): SuspendableFuture {
        if (bot.preferences.isMerchant) return SuspendableFutureFailed
        // Check if a shop is open.
        val shopInterface = bot.overlays[ShopInterface::class] ?: return SuspendableFutureFailed

        val definition = itemDef(id)
        if (shopInterface.shop.buyPolicy == BuyPolicy.ALL && !definition.isStackable && definition.notedId.isPresent) {
            bot.log("I need to withdraw ${itemName(id)} as notes before selling here.")
            return SuspendableFutureFailed
        }

        bot.log("Selling $amount of ${itemName(id)}.")
        val inventoryIndex = bot.inventory.computeIndexForId(id)
        if (inventoryIndex == -1) {
            // Bot doesn't have the item.
            bot.log("I don't have that item.")
            return SuspendableFutureFailed
        }
        if (!shopInterface.shop.computeCanSell(id)) {
            bot.log("I cannot sell ${itemName(id)} item here.")
            return SuspendableFutureFailed
        }

        val amountBefore = bot.inventory.computeAmountForId(id)
        when (amount) {
            1 -> bot.output.sendItemWidgetClick(2, inventoryIndex, 3823, id)
            5 -> bot.output.sendItemWidgetClick(3, inventoryIndex, 3823, id)
            10 -> bot.output.sendItemWidgetClick(4, inventoryIndex, 3823, id)
            100 -> bot.output.sendItemWidgetClick(5, inventoryIndex, 3823, id)
            else -> throw IllegalStateException("Invalid amount.")
        }
        val soldItemCond = SuspendableCondition { bot.inventory.computeAmountForId(id) < amountBefore }
        return soldItemCond.submit(5) // Unsuspend when the inventory amount decreases.
    }

    /**
     * Keeps the one-item acquisition API used by tool selection.
     */
    suspend fun obtainWantedItem(id: Int): Boolean = obtainWantedItem(id, 1)

    /**
     * Obtains the minimum carried quantity through owned bank stock and normal shop purchases.
     */
    suspend fun obtainWantedItem(id: Int, amount: Int): Boolean {
        require(amount > 0)
        BotTradeIntentions.recordSupplies(bot, listOf(Item(id, amount)), stockTarget = amount)
        return id in obtainWantedItems(mapOf(id to amount))
    }

    /**
     * Attempts to carry or equip one of each requested item, returning the ids available when the attempt ends.
     * Unavailable items do not prevent attempts to obtain the rest of the basket.
     */
    suspend fun obtainWantedItems(ids: Collection<Int>): Set<Int> = obtainWantedItems(ids.associateWith { 1 })

    /**
     * Attempts the requested minimum carried quantities through owned bank stock and normal shop purchases.
     * Each purchase rechecks access, stock, price, funds, capacity and actual inventory progress.
     * Basket items remain carried when depositing other inventory. Partial purchases remain owned on failure;
     * the returned ids meet their targets when the attempt ends. The whole basket shares one two-minute
     * deadline, including banking, travel and every purchase.
     */
    suspend fun obtainWantedItems(amounts: Map<Int, Int>): Set<Int> = obtainWantedItems(amounts, null)

    /**
     * Obtains owned stock normally, then buys only from GP shops within one shared spending limit.
     * Missing unit ceilings prohibit purchases of that item. Every native purchase rechecks the current quote
     * after travel and executes synchronously within its total and unit ceilings, in batches of at most one hundred.
     * Partial purchases remain owned; the ordinary unguarded acquisition API retains its existing behavior.
     */
    suspend fun obtainWantedItems(amounts: Map<Int, Int>, maximumCoins: Int,
                                  maximumUnitPrices: Map<Int, Long>,
                                  recordSpending: (Int) -> Unit = {}): Set<Int> {
        require(maximumCoins >= 0)
        require(maximumUnitPrices.values.all { it > 0L })
        val limits = amounts.keys.mapNotNull { id ->
            BotTradeIntentions.productionLimit(bot, id)?.let { id to it }
        }.toMap()
        return obtainWantedItems(amounts,
            PurchaseBudget(maximumCoins, maximumUnitPrices.toMap(), recordSpending, limits))
    }

    private suspend fun obtainWantedItems(amounts: Map<Int, Int>, budget: PurchaseBudget?): Set<Int> {
        require(amounts.values.all { it > 0 })
        val targets = amounts.toMap()
        fun acquired() = targets.filter { (id, amount) ->
            bot.inventory.computeAmountForId(id).toLong() + bot.equipment.computeAmountForId(id) >= amount
        }.keys
        if (acquired().size == targets.size) return targets.keys
        if (budget == null) {
            val owner = bot.scriptStack.current()
            if (owner != null && owner !is TradeBotScript) {
                val attempted = marketAttempts.getOrPut(owner) { hashSetOf() }
                for ((id, amount) in targets) {
                    if (id in acquired() || bot.bank.computeAmountForId(id).toLong() +
                        bot.inventory.computeAmountForId(id) >= amount) {
                        attempted.remove(id)
                        continue
                    }
                    if (attempted.remove(id)) continue
                    val deadline = System.currentTimeMillis() + 45_000L
                    val trade = TradeBotScript.buySpecific(bot, id, amount, deadline) ?: continue
                    attempted.add(id)
                    trade.duration = 45.seconds
                    bot.scriptStack.pushHead(trade)
                    return acquired()
                }
            }
        }
        withTimeoutOrNull(120.seconds) {
            try {
                for ((id, amount) in targets) {
                    if (!ActionSafety.NON_COMBAT.await(bot)) break
                    acquireWantedItem(id, amount, targets.keys, budget)
                }
            } finally {
                bot.overlays.closeWindows()
            }
        }
        return acquired()
    }

    /**
     * Attempts one target while preserving the rest of the basket during banking.
     */
    private suspend fun acquireWantedItem(id: Int, amount: Int, basket: Set<Int>, budget: PurchaseBudget?): Boolean {
        fun carried() = bot.inventory.computeAmountForId(id).toLong() + bot.equipment.computeAmountForId(id)
        if (carried() >= amount) return true
        if (!ActionSafety.NON_COMBAT.await(bot)) return false
        if (bot.bank.contains(id)) {
            if (!handler.banking.travelToBankOpen()) return false
            handler.banking.clickBankingMode(false)
            val banked = minOf(bot.bank.computeAmountForId(id).toLong(), amount - carried()).toInt()
            if (banked > 0) {
                if (!bot.inventory.hasSpaceFor(Item(id, banked)) &&
                    !handler.banking.depositInventory(basket)) return false
                if (!handler.banking.withdraw(Item(id, banked))) return false
            }
        }
        if (carried() >= amount) return true
        if (budget != null) return acquireQuotedItem(id, amount, basket, budget)
        if (id == BotTaverleyKeys.DUSTY_KEY && amount == 1 && BotTaverleyKeys.obtain(bot, basket)) return true
        if (!ActionSafety.NON_COMBAT.allows(bot)) return false
        val shops = world.shops.values().filter { id in it.itemIds && it.botAccess.apply(bot) }
            .sortedWith(compareBy<io.luna.game.model.item.shop.Shop> { it.currency.id != 995 }
                .thenBy { it.computeBuyValue(id, 1).orElse(Int.MAX_VALUE) })
        if (shops.isEmpty()) return id == 1793 && acquireWoadLeaves(amount, basket)
        bot.actions.interruptWeak()
        bot.overlays.closeWindows()
        for (shop in shops) {
            while (carried() < amount) {
                if (!ActionSafety.NON_COMBAT.await(bot)) return false
                if (!shop.botAccess.apply(bot) || shop.items.computeAmountForId(id) < 1) break
                val price = shop.computeBuyValue(id, 1).orElse(-1)
                if (price < 0) break
                val currency = shop.currency.id
                val heldCoins = bot.inventory.computeAmountForId(currency)
                if (heldCoins.toLong() + bot.bank.computeAmountForId(currency) < price) break
                if (heldCoins < price || !bot.inventory.hasSpaceFor(Item(id))) {
                    if (!handler.banking.travelToBankOpen()) break
                    handler.banking.clickBankingMode(false)
                    if (!bot.inventory.hasSpaceForAll(listOf(Item(id), Item(currency, maxOf(1, price))))) {
                        if (!handler.banking.depositInventory(basket + currency)) break
                    }
                    // Carry enough for this purchase target instead of returning to the bank for every rune.
                    if (carried() >= amount) return true
                    val needed = (amount - carried()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    val stocked = shop.items.computeAmountForId(id)
                    if (stocked < 1) break
                    val quoted = shop.computeBuyValue(id, minOf(needed, stocked)).orElse(price)
                    val wallet = minOf(heldCoins.toLong() + bot.bank.computeAmountForId(currency),
                        maxOf(price.toLong(), quoted.toLong() + needed)).coerceAtMost(Int.MAX_VALUE.toLong())
                    val missing = (wallet - bot.inventory.computeAmountForId(currency)).coerceAtLeast(0).toInt()
                    if (missing > 0 && !handler.banking.withdraw(Item(currency, missing))) break
                }
                if (!ActionSafety.NON_COMBAT.await(bot)) return false
                bot.overlays.closeWindows()
                if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) break
                if (!ActionSafety.NON_COMBAT.await(bot) || !shop.botAccess.apply(bot)) break
                bot.overlays.open(ShopInterface(shop))
                val before = carried()
                val remaining = amount - before
                if (remaining <= 0) return true
                val bought = buyUpTo(shop, id, remaining.coerceAtMost(100L).toInt(),
                    bot.inventory.computeAmountForId(currency))
                if (bot.overlays[ShopInterface::class]?.shop !== shop || !bought ||
                    carried() <= before) break
            }
            if (carried() >= amount) return true
        }
        return id == 1793 && acquireWoadLeaves(amount, basket)
    }

    /**
     * Preserves the basket while staging only authorized GP, refreshing quotes after each suspension.
     * Native quoted purchases cannot race another queued shop click between price validation and payment.
     */
    private suspend fun acquireQuotedItem(id: Int, amount: Int, basket: Set<Int>, budget: PurchaseBudget): Boolean {
        val ceiling = budget.maximumUnitPrices[id] ?: return false
        val coins = Currency.COINS.id
        if (id == coins || budget.remainingCoins < 1) return false
        fun carried() = bot.inventory.computeAmountForId(id).toLong() + bot.equipment.computeAmountForId(id)
        val shops = world.shops.values().filter {
            it.currency == Currency.COINS && id in it.itemIds && it.botAccess.apply(bot)
        }.sortedBy { it.computeBuyValue(id, 1).orElse(Int.MAX_VALUE) }
        bot.actions.interruptWeak()
        bot.overlays.closeWindows()
        for (shop in shops) {
            while (carried() < amount && budget.remainingCoins > 0) {
                if (!ActionSafety.NON_COMBAT.await(bot)) return false
                if (!shop.botAccess.apply(bot) || shop.items.computeAmountForId(id) < 1) break
                val price = shop.computeBuyValue(id, 1).orElse(-1)
                if (price < 1 || price.toLong() > ceiling || price > budget.remainingCoins) break
                val held = bot.inventory.computeAmountForId(coins)
                if (held.toLong() + bot.bank.computeAmountForId(coins) < price) break
                if (held < price || !bot.inventory.hasSpaceFor(Item(id))) {
                    if (!handler.banking.travelToBankOpen() || !ActionSafety.NON_COMBAT.await(bot)) break
                    handler.banking.clickBankingMode(false)
                    if (!bot.inventory.hasSpaceForAll(listOf(Item(id), Item(coins))) &&
                        !handler.banking.depositInventory(basket + coins)) break
                    if (!ActionSafety.NON_COMBAT.await(bot)) return false
                    if (carried() >= amount) return true
                    val wallet = minOf(budget.remainingCoins.toLong(),
                        bot.inventory.computeAmountForId(coins).toLong() + bot.bank.computeAmountForId(coins))
                    val missing = (wallet - bot.inventory.computeAmountForId(coins)).coerceAtLeast(0L).toInt()
                    if (missing > 0 && !handler.banking.withdraw(Item(coins, missing))) break
                }
                if (!ActionSafety.NON_COMBAT.await(bot)) return false
                bot.overlays.closeWindows()
                if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) break
                if (!ActionSafety.NON_COMBAT.await(bot) || !shop.botAccess.apply(bot)) break
                bot.overlays.open(ShopInterface(shop))
                val before = carried()
                val production = BotTradeIntentions.productionLimit(bot, id)
                val captured = budget.productionLimits[id]
                if (captured != null && (production == null || production.expires != captured.expires ||
                    production.identity !== captured.identity)) break
                val deficit = production?.let { (it.target.toLong() - bot.totalItemAmount(id)).coerceAtLeast(0L) }
                    ?: Int.MAX_VALUE.toLong()
                val unitCeiling = minOf(ceiling, production?.maximumUnitPrice ?: Long.MAX_VALUE)
                val maximum = minOf((amount - before).coerceAtLeast(0L),
                    shop.items.computeAmountForId(id).toLong(), 100L, deficit).toInt()
                val wallet = minOf(budget.remainingCoins.toLong(), bot.inventory.computeAmountForId(coins).toLong(),
                    production?.maximumCoins ?: Long.MAX_VALUE).toInt()
                var quantity = maximum
                var quote = -1
                while (quantity > 0) {
                    quote = shop.computeBuyValue(id, quantity).orElse(-1)
                    if (quote in 1..wallet &&
                        quote.toLong() <= unitCeiling.coerceAtMost(Int.MAX_VALUE.toLong()) * quantity &&
                        bot.inventory.hasSpaceFor(Item(id, quantity))) break
                    quantity--
                }
                if (quantity < 1) break
                val beforeCoins = bot.inventory.computeAmountForId(coins)
                val bought = buyQuoted(shop, id, quantity, quote, captured ?: production)
                val spent = (beforeCoins - bot.inventory.computeAmountForId(coins)).coerceAtLeast(0)
                budget.remainingCoins = (budget.remainingCoins - spent).coerceAtLeast(0)
                if (spent > 0) budget.recordSpending(spent)
                if (!bought || carried() <= before) break
                delay(1.seconds)
            }
            if (carried() >= amount) return true
        }
        return false
    }

    /**
     * Fulfils real woad demand through Wyson's native twenty-coin offer for two leaves. Only owned coins are
     * withdrawn, other requested basket items remain carried, and every purchase verifies actual leaf progress.
     * The caller's shared acquisition deadline includes banking, webwalking and all dialogue attempts.
     */
    private suspend fun acquireWoadLeaves(amount: Int, basket: Set<Int>): Boolean {
        val leaf = 1793
        val coins = 995
        val response = "Yes please, I need woad leaves."
        val offer = "How about 20 coins?"
        fun carried() = bot.inventory.computeAmountForId(leaf)
        while (carried() < amount) {
            if (!ActionSafety.NON_COMBAT.await(bot)) return false
            val funds = bot.inventory.computeAmountForId(coins).toLong() + bot.bank.computeAmountForId(coins)
            if (funds < 20) return false
            if (bot.inventory.computeAmountForId(coins) < 20 || !bot.inventory.hasSpaceFor(Item(leaf, 2))) {
                if (!handler.banking.travelToBankOpen()) return false
                handler.banking.clickBankingMode(false)
                if (!bot.inventory.hasSpaceForAll(listOf(Item(leaf, 2), Item(coins))) &&
                    !handler.banking.depositInventory(basket + coins)) return false
                val needed = (amount.toLong() - carried()).coerceAtLeast(0)
                if (needed == 0L) return true
                val wallet = minOf(funds, ((needed + 1) / 2) * 20, Int.MAX_VALUE.toLong())
                val missing = (wallet - bot.inventory.computeAmountForId(coins)).coerceAtLeast(0).toInt()
                if (missing > 0 && !handler.banking.withdraw(Item(coins, missing))) return false
            }
            if (!bot.inventory.hasSpaceFor(Item(leaf, 2)) || bot.inventory.computeAmountForId(coins) < 20) return false
            bot.overlays.closeWindows()
            if (!handler.webWalkTo(SubZone.FALADOR_EAST_BANK)) return false
            val npc = world.locator.findNearestNpc(SubZone.FALADOR_EAST_BANK.inside, 80) {
                it.id == 36 && it.state == EntityState.ACTIVE
            } ?: return false
            if (!handler.interactions.approach(npc, timeout = 60.seconds,
                    canContinue = { ActionSafety.NON_COMBAT.allows(bot) })) return false
            if (!bot.inventory.hasSpaceFor(Item(leaf, 2)) || bot.inventory.computeAmountForId(coins) < 20) return false
            val before = carried()
            var queue: DialogueQueue? = null
            var pageOwned: DialogueInterface? = null
            var optionSelected: OptionDialogue? = null
            var step = 0
            var replacementExpected = false
            val previous = bot.dialogues
            try {
                if (!handler.interactions.interact(1, npc) { ActionSafety.NON_COMBAT.allows(bot) }) return false
                while (ActionSafety.NON_COMBAT.allows(bot) && carried() == before) {
                    val page = bot.overlays.getOverlay(DialogueInterface::class.java)
                    val expected = when (page) {
                        is NpcDialogue -> page.npcId == 36
                        is PlayerDialogue -> page.lines == listOf(if (step >= 2) offer else response)
                        is OptionDialogue -> page === optionSelected || (if (step == 0) response else offer) in page.options
                        else -> false
                    }
                    val current = bot.dialogues
                    if (queue == null) {
                        if (expected && current != null && current !== previous) queue = current
                    } else if (current !== queue) {
                        if (!replacementExpected || !expected || current == null) return false
                        queue = current
                        replacementExpected = false
                    }
                    if (queue != null && page != null) {
                        if (!expected) return false
                        pageOwned = page
                        if (page is OptionDialogue && page !== optionSelected) {
                            if (step >= 2 || !handler.widgets.clickDialogueOption(if (step == 0) response else offer))
                                return false
                            optionSelected = page
                            step++
                            replacementExpected = true
                        }
                    }
                    delay(1.seconds)
                }
                if (carried() <= before) return false
            } finally {
                // Close only the exact service page this attempt observed; do not advance a reward callback.
                if (queue != null && bot.dialogues === queue &&
                    bot.overlays.getOverlay(DialogueInterface::class.java) === pageOwned) {
                    bot.overlays.closeWindows()
                    bot.resetDialogues()
                }
            }
        }
        return true
    }
}
