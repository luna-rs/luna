package api.bot.action

import api.bot.SuspendableCondition
import api.bot.Suspendable.delay
import api.bot.SuspendableFuture
import api.bot.SuspendableFuture.SuspendableFutureFailed
import api.bot.action.trading.BotTradeIntentions
import api.bot.zone.SubZone
import api.bot.script.BotScript.ActionSafety
import io.luna.game.model.mob.bot.Bot
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.item.Item
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

/**
 * A [BotActionHandler] implementation for shop related actions.
 */
class BotShopActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Executes one price-protected purchase on the game thread through the native shop transaction.
     * The expected overlay, access, quantity, space and current quote are checked without suspending before buying.
     * Ordinary queued shop clicks retain their existing behavior; this action is for finite bargain purchases.
     */
    fun buyQuoted(shop: Shop, id: Int, amount: Int, maxTotalCost: Int): Boolean {
        if (amount !in 1..100 || maxTotalCost < 1 || !ActionSafety.NON_COMBAT.allows(bot) ||
            bot.isConnectedPlayer && !bot.isAutomationActive ||
            shop.currency != Currency.COINS || !shop.botAccess.apply(bot) ||
            bot.overlays[ShopInterface::class]?.shop !== shop) return false
        val index = shop.items.computeIndexForId(id)
        if (index < 0) return false
        val item = shop.items[index] ?: return false
        if (item.id != id || item.isDynamic || item.amount < amount ||
            bot.inventory.computeAmountForId(id).toLong() + amount > Int.MAX_VALUE ||
            !bot.inventory.hasSpaceFor(Item(id, amount))) return false
        val quoted = shop.computeBuyValue(id, amount).orElse(-1)
        val coins = bot.inventory.computeAmountForId(Currency.COINS.id)
        if (quoted !in 1..maxTotalCost || coins < quoted) return false
        val before = bot.inventory.computeAmountForId(id)
        return shop.buy(bot, index, amount) && bot.inventory.computeAmountForId(id) == before + amount &&
            bot.inventory.computeAmountForId(Currency.COINS.id) == coins - quoted
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
     * Attempts to sell either 1, 5, or 10 of an item.
     */
    private fun sell(id: Int, amount: Int): SuspendableFuture {
        if (bot.preferences.isMerchant) return SuspendableFutureFailed
        // Check if a shop is open.
        val shopInterface = bot.overlays[ShopInterface::class] ?: return SuspendableFutureFailed

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
    suspend fun obtainWantedItems(amounts: Map<Int, Int>): Set<Int> {
        require(amounts.values.all { it > 0 })
        val targets = amounts.toMap()
        fun acquired() = targets.filter { (id, amount) ->
            bot.inventory.computeAmountForId(id).toLong() + bot.equipment.computeAmountForId(id) >= amount
        }.keys
        if (acquired().size == targets.size) return targets.keys
        withTimeoutOrNull(120.seconds) {
            try {
                for ((id, amount) in targets) {
                    if (!ActionSafety.NON_COMBAT.await(bot)) break
                    acquireWantedItem(id, amount, targets.keys)
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
    private suspend fun acquireWantedItem(id: Int, amount: Int, basket: Set<Int>): Boolean {
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
                val buy = when {
                    remaining >= 100 && shop.items.computeAmountForId(id) >= 100 &&
                        shop.computeBuyValue(id, 100).orElse(-1) in 0..bot.inventory.computeAmountForId(currency) -> buy100(id)
                    remaining >= 10 && shop.items.computeAmountForId(id) >= 10 &&
                        shop.computeBuyValue(id, 10).orElse(-1) in 0..bot.inventory.computeAmountForId(currency) -> buy10(id)
                    remaining >= 5 && shop.items.computeAmountForId(id) >= 5 &&
                        shop.computeBuyValue(id, 5).orElse(-1) in 0..bot.inventory.computeAmountForId(currency) -> buy5(id)
                    else -> buy1(id)
                }
                if (bot.overlays[ShopInterface::class]?.shop !== shop || !buy.await() ||
                    carried() <= before) break
            }
            if (carried() >= amount) return true
        }
        return id == 1793 && acquireWoadLeaves(amount, basket)
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
