package api.bot.action.trading

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.isRealBot
import api.bot.script.BotScript.ActionSafety
import api.predef.world
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.ItemEconomyDefinition
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopManager
import io.luna.game.model.mob.bot.Bot

/**
 * Samples sold-in general-store stock without repeating a world-wide shop scan for each merchant.
 * One shared cursor visits a bounded number of slots, with a separate global valuation and dispatch allowance.
 * Expensive personal reserve checks happen only after a candidate has passed the cheap stock and price checks.
 * A quote reserves no stock or money; the purchase script must revalidate immediately before the native purchase.
 *
 * @author lare96
 */
object MerchantStoreBargains {
    internal const val MAX_SLOTS_PER_POLL = 16
    internal const val MAX_SLOTS_PER_TICK = 128
    internal const val MAX_QUOTES_PER_POLL = 2
    internal const val MAX_QUOTES_PER_TICK = 4
    private const val MAX_BUDGET = 25_000
    private const val MIN_PROFIT = 100L
    private const val QUOTE_LIFETIME_MILLIS = 120_000L
    private const val CACHE_LIFETIME_MILLIS = 60_000L
    private const val ADMISSION_IDLE_MILLIS = 4_200L

    private var Bot.nextBargainCheck by Attr.long().persist("merchant_store_check_at")
    private var Bot.lastBargainRound by Attr.long { Long.MIN_VALUE }
    private var cachedWorld: World? = null
    private var cachedManager: ShopManager? = null
    private var cachedShops: List<Shop> = emptyList()
    private var refreshAt = 0L
    private var shopCursor = 0
    private var slotCursor = 0
    private var budgetTick = Long.MIN_VALUE
    private var slotsUsed = 0
    private var quotesUsed = 0
    private var dispatchesUsed = 0
    private var admissionRound = 0L
    private var lastAdmissionAt = 0L

    /**
     * An expiring, bounded opportunity, retaining the actual shop identity for stale-registry rejection.
     * Budget is the most this attempt may spend, rather than an assumed cost or a reservation.
     *
     * @author lare96
     */
    data class Deal(val shop: Shop, val itemId: Int, val quantity: Int, val budget: Int,
                    val resaleUnit: Long, val expiresAt: Long)

    /**
     * Offers at most one bounded purchase per world tick. Cooldown and exhausted global allowances return before
     * ownership, catalogue or price work. Due merchants get one admission per round before earlier owners can repeat.
     * A quiet interval longer than every denied owner's retry allows another round without tracking an owner queue.
     */
    fun poll(bot: Bot, nowMillis: Long = System.currentTimeMillis()): Deal? {
        if (nowMillis < bot.nextBargainCheck || !permitted(bot)) return null
        val current = world
        if (cachedWorld !== current) {
            cachedWorld = current
            cachedManager = null
            budgetTick = Long.MIN_VALUE
            refreshAt = 0L
            shopCursor = 0
            slotCursor = 0
            admissionRound = 0L
            lastAdmissionAt = nowMillis
        }
        val tick = current.currentTick
        if (budgetTick != tick) {
            budgetTick = tick
            slotsUsed = 0
            quotesUsed = 0
            dispatchesUsed = 0
        }
        if (nowMillis - lastAdmissionAt > ADMISSION_IDLE_MILLIS) {
            admissionRound++
            lastAdmissionAt = nowMillis
        }
        if (bot.lastBargainRound == admissionRound || slotsUsed >= MAX_SLOTS_PER_TICK ||
            quotesUsed >= MAX_QUOTES_PER_TICK || dispatchesUsed > 0) {
            bot.nextBargainCheck = nowMillis + retryDelay(bot, tick)
            return null
        }
        refreshShops(nowMillis)
        bot.nextBargainCheck = nowMillis + cooldown(bot)
        bot.lastBargainRound = admissionRound
        lastAdmissionAt = nowMillis
        if (cachedShops.isEmpty()) return null
        val budget = spendingLimit(bot)
        if (budget < 1) return null
        var examined = 0
        var valued = 0
        var retained: Map<Int, Int>? = null
        while (examined < MAX_SLOTS_PER_POLL && slotsUsed < MAX_SLOTS_PER_TICK &&
            valued < MAX_QUOTES_PER_POLL && quotesUsed < MAX_QUOTES_PER_TICK) {
            val shop = cachedShops[shopCursor]
            val slot = slotCursor
            slotCursor++
            if (slotCursor >= shop.items.capacity()) {
                slotCursor = 0
                shopCursor = (shopCursor + 1) % cachedShops.size
            }
            examined++
            slotsUsed++
            val item = shop.items[slot] ?: continue
            if (shop.isOriginallyStocked(slot) || item.amount < 1 || item.isDynamic ||
                !eligibleItem(item.id) || !shop.botAccess.apply(bot)) continue
            valued++
            quotesUsed++
            val candidate = quote(bot, shop, item.id, Int.MAX_VALUE, budget, Long.MAX_VALUE,
                nowMillis + QUOTE_LIFETIME_MILLIS) {
                retained ?: BotTradeIntentions.retainedStock(bot).also { retained = it }
            } ?: continue
            dispatchesUsed++
            return candidate
        }
        return null
    }

    /**
     * Reprices only the selected stack after travel or a previous click. Callers must perform the native buy
     * without suspending after this check, otherwise stock or prices can change before payment is processed.
     */
    fun revalidate(bot: Bot, deal: Deal, maxQuantity: Int = deal.quantity, budget: Int = deal.budget,
                   nowMillis: Long = System.currentTimeMillis()): Deal? {
        if (!permitted(bot) || nowMillis >= deal.expiresAt || maxQuantity < 1 || budget < 1 ||
            world.shops.lookup(deal.shop.name).orElse(null) !== deal.shop) return null
        return quote(bot, deal.shop, deal.itemId, minOf(maxQuantity, deal.quantity),
            minOf(budget, deal.budget, spendingLimit(bot)), deal.resaleUnit, deal.expiresAt) {
            BotTradeIntentions.retainedStock(bot)
        }
    }

    /**
     * Backs off failed or expired attempts without changing native stock, prices or existing personal demands.
     */
    fun failed(bot: Bot, nowMillis: Long = System.currentTimeMillis()) {
        bot.nextBargainCheck = maxOf(bot.nextBargainCheck, nowMillis + 180_000L)
    }

    private fun permitted(bot: Bot): Boolean = bot.isRealBot() && bot.preferences.isMerchant &&
        bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot)

    private fun cooldown(bot: Bot): Long = 60_000L + Math.floorMod(bot.usernameHash, 120_001L)

    private fun retryDelay(bot: Bot, tick: Long): Long =
        600L + Math.floorMod(bot.usernameHash xor (tick * -7046029254386353131L), 2_401L)

    private fun spendingLimit(bot: Bot): Int =
        minOf(MAX_BUDGET.toLong(), bot.itemTracker.count(995).toLong() / 5).toInt()

    private fun refreshShops(nowMillis: Long) {
        val manager = world.shops
        if (cachedManager === manager && nowMillis < refreshAt) return
        val nextShop = cachedShops.getOrNull(shopCursor).takeIf { cachedManager === manager }
        val refreshed = manager.values().filter(::eligibleShop)
        val nextIndex = refreshed.indexOfFirst { it === nextShop }
        cachedManager = manager
        cachedShops = refreshed
        refreshAt = nowMillis + CACHE_LIFETIME_MILLIS
        shopCursor = nextIndex.coerceAtLeast(0)
        if (nextIndex < 0 || slotCursor !in 0 until refreshed[nextIndex].items.capacity()) slotCursor = 0
    }

    private fun eligibleShop(shop: Shop): Boolean = shop.currency == Currency.COINS &&
        shop.buyPolicy == BuyPolicy.ALL && !shop.name.equals("Bot General Store", ignoreCase = true)

    private fun eligibleItem(id: Int): Boolean {
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return false
        return id != 995 && id != 0 && definition.isTradeable && !definition.isNoted
    }

    private fun knownGuide(id: Int): Long = world.economy.prices[id]?.price?.toLong()
        ?: ItemEconomyDefinition.ALL.get(id).orElse(null)?.initialPrice?.toLong() ?: 0L

    private fun quote(bot: Bot, shop: Shop, id: Int, requested: Int, budget: Int, resaleCeiling: Long,
                      expiresAt: Long, reserves: () -> Map<Int, Int>): Deal? {
        if (budget < 1 || !eligibleShop(shop) || !shop.botAccess.apply(bot) || !eligibleItem(id)) return null
        val slot = shop.items.computeIndexForId(id)
        if (slot < 0 || shop.isOriginallyStocked(slot)) return null
        val stock = shop.items[slot] ?: return null
        if (stock.amount < 1 || stock.isDynamic) return null
        val definition = stock.itemDef
        val guide = knownGuide(id)
        if (guide <= 0) return null
        val resale = minOf(resaleCeiling, guide * 4 / 5)
        if (resale <= 0) return null
        val note = definition.notedId.orElse(-1)
        val owned = bot.itemTracker.count(id).toLong() +
            if (note >= 0 && note != id) bot.itemTracker.count(note).toLong() else 0L
        val stashLimit = if (definition.isStackable) 200 else 26
        val limit = minOf(requested.toLong(), stock.amount.toLong(), if (definition.isStackable) 100L else 26L,
            (stashLimit - owned).coerceAtLeast(0)).toInt()
        if (limit < 1) return null
        var low = 1
        var high = limit
        var quantity = 0
        var cost = 0
        while (low <= high) {
            val middle = (low + high) ushr 1
            val price = shop.computeBuyValue(id, middle).orElse(-1)
            if (price > 0 && price <= budget && price.toLong() <= resale * middle * 85 / 100) {
                quantity = middle
                cost = price
                low = middle + 1
            } else high = middle - 1
        }
        if (quantity == 0) return null
        val minimumStackValue = Luna.settings().bots().merchantSaleMinimumStackValue()
        if (quantity * resale - cost < MIN_PROFIT || (owned + quantity) * resale <= minimumStackValue) return null
        // New usable gear can become a retained personal copy after purchase, even when it is not currently owned.
        val personalCopy = EquipmentDefinition.ALL.get(id).orElse(null)?.let {
            if (it.meetsAllRequirements(bot)) 1 else 0
        } ?: 0
        val retained = maxOf(reserves()[id] ?: 0, personalCopy).toLong()
        val resaleStock = (owned + quantity - retained).coerceAtLeast(0)
        val acquiredForResale = minOf(quantity.toLong(), resaleStock)
        if (acquiredForResale * resale - cost < MIN_PROFIT ||
            resaleStock * resale <= minimumStackValue) return null
        return Deal(shop, id, quantity, budget, resale, expiresAt)
    }
}
