package api.bot.action.trading

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotSupplySources
import api.predef.ext.allSuitableFood
import api.predef.ext.canUseWantedItem
import api.predef.ext.totalItemAmount
import engine.bot.gear.BotItemTracker.Companion.itemOwnershipRevision
import game.player.item.consume.food.Food
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.item.Item
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.mob.bot.Bot

/**
 * Keeps generated meals in one physical-piece reserve while literal wishes and recipe inputs keep exact ids.
 * Per-bite healing governs suitability; remaining native portions govern value, never a replacement HP quota.
 *
 * @author lare96
 */
object BotFoodDemand {
    /**
     * One current activity's food policy and original authorized purse, retained through ordinary account saves.
     *
     * @author lare96
     */
    private data class Request(val preferred: Int, val minimum: Int, val target: Int, val minimumHeal: Int,
                               val maximumHeal: Int, val expires: Long, val maximumCoins: Int,
                               val prices: MutableMap<Int, Long> = HashMap())

    /**
     * Lazy account state and a short-lived concrete candidate for the bounded personal production graph.
     *
     * @author lare96
     */
    private class State {
        var loaded = false
        var request: Request? = null
        var selected = -1
        var selectedUntil = 0L
        var revision = Long.MIN_VALUE
    }

    /**
     * Real finite stock or one funded resting bid; quantities are physical packages, not equivalent whole meals.
     *
     * @author lare96
     */
    internal data class Basket(val id: Int, val quantity: Int, val unitPrice: Long)

    private val state = Attr.obj { State() }
    private var Bot.savedFood by Attr.string().persist("bot_generated_food_need")
    private val weights = linkedMapOf(
        Food.SHRIMP to 30, Food.HERRING to 22, Food.ANCHOVIES to 18, Food.CHICKEN to 12,
        Food.MEAT to 10, Food.BANANA to 5, Food.REDBERRY_PIE to 2,
        Food.TROUT to 32, Food.PIKE to 22, Food.COD to 18, Food.MACKEREL to 14,
        Food.CAKE to 7, Food.CHOCOLATE_CAKE to 5, Food.MEAT_PIE to 5,
        Food.PLAIN_PIZZA to 3, Food.APPLE_PIE to 3,
        Food.SALMON to 30, Food.TUNA to 26, Food.BASS to 18, Food.LOBSTER to 15,
        Food.PEACH to 3, Food.MEAT_PIZZA to 4, Food.ANCHOVY_PIZZA to 3,
        Food.PINEAPPLE_PIZZA to 2, Food.SUMMER_PIE to 2,
        Food.SWORDFISH to 25, Food.MONKFISH to 24, Food.SHARK to 20,
        Food.KARAMBWAN to 10, Food.MANTA_RAY to 5, Food.SEA_TURTLE to 5, Food.TUNA_POTATO to 3)
    private val foods = weights.keys.flatMap { it.ids }.sorted()
    private val foodIds = foods.toSet()

    private fun live(bot: Bot, now: Long = System.currentTimeMillis()): Request? {
        val value = bot.attributes()[state.attr]
        if (!value.loaded) {
            value.loaded = true
            val fields = bot.savedFood.split(',')
            if (fields.size == 7) {
                val numbers = fields.map { it.toLongOrNull() }
                if (numbers.none { it == null }) {
                    val values = numbers.filterNotNull()
                    val preferred = values[0]
                    val minimum = values[1]
                    val target = values[2]
                    val min = values[3]
                    val max = values[4]
                    val expires = values[5]
                    val coins = values[6]
                    if (preferred.toInt() in foods && minimum in 1L..28L && target in minimum..250L &&
                        min in 1L..22L && max in min..22L && expires > now && coins in 0L..Int.MAX_VALUE.toLong())
                        value.request = Request(preferred.toInt(), minimum.toInt(), target.toInt(), min.toInt(),
                            max.toInt(), expires, coins.toInt())
                }
            }
        }
        if (value.request?.expires?.let { it <= now } == true) {
            value.request = null
            value.selectedUntil = 0L
            bot.savedFood = ""
        }
        return value.request
    }

    /**
     * Replaces generated activity food without changing any independent literal or finite-production target.
     */
    fun request(bot: Bot, minimum: Int = 14, minimumHeal: Int = 0,
                maximumHeal: Int = Int.MAX_VALUE, now: Long = System.currentTimeMillis()): Food? {
        val suitable = bot.preferences.allSuitableFood().toSet()
        val tierMaximum = suitable.maxOfOrNull { it.heal } ?: 0
        val candidates = weights.keys.filter { (it in suitable || minimumHeal > tierMaximum) &&
            it.heal in minimumHeal..maximumHeal && bot.canUseWantedItem(it.id) &&
            ItemDefinition.ALL.get(it.id).map { definition -> definition.isTradeable }.orElse(false) }
        if (candidates.isEmpty()) {
            bot.attributes()[state.attr].apply { request = null; loaded = true; selectedUntil = 0L }
            bot.savedFood = ""
            return null
        }
        val previous = live(bot, now)
        val preferred = previous?.preferred?.let(Food.ID_TO_FOOD::get)?.takeIf { it in candidates }
            ?: choose(candidates, bot.username.orEmpty().hashCode())
        val stockpiler = bot.personality.isDextrous || bot.personality.isIntelligent
        val usefulMinimum = if (minimumHeal > 0) minimumHeal else candidates.minOf { it.heal }
        val usefulMaximum = if (minimumHeal > 0) maximumHeal.coerceAtMost(22) else candidates.maxOf { it.heal }
        val sameContext = previous?.takeIf { it.minimumHeal == usefulMinimum && it.maximumHeal == usefulMaximum }
        val capturedPurse = sameContext?.maximumCoins?.takeIf { it > 0 } ?: currentAllowance(bot)
        val value = Request(preferred.id, minimum.coerceIn(1, 28), maxOf(minimum.coerceIn(1, 28),
            if (stockpiler) 250 else 112), usefulMinimum, usefulMaximum,
            sameContext?.expires ?: (now + if (stockpiler) 7_200_000L else 1_800_000L), capturedPurse,
            sameContext?.prices ?: HashMap())
        bot.attributes()[state.attr].apply { request = value; loaded = true; selectedUntil = 0L }
        bot.savedFood = listOf(value.preferred, value.minimum, value.target, value.minimumHeal,
            value.maximumHeal, value.expires, value.maximumCoins).joinToString(",")
        return preferred
    }

    fun active(bot: Bot): Boolean = live(bot) != null

    private fun compatible(request: Request, id: Int): Boolean = id in foodIds &&
        Food.ID_TO_FOOD[id]?.heal?.let { it in request.minimumHeal..request.maximumHeal } == true

    fun compatible(bot: Bot, id: Int): Boolean = live(bot)?.let { compatible(it, id) } == true

    fun handles(bot: Bot, id: Int): Boolean = compatible(bot, id) &&
        BotTradeIntentions.literalSupplyTarget(bot, id) < 1

    fun identity(bot: Bot): String? = live(bot)?.let { "food:${it.minimumHeal}:${it.maximumHeal}" }

    fun reserve(bot: Bot): Int = live(bot)?.target ?: 0

    private fun currentAllowance(bot: Bot, offeredCoins: Long = 0L): Int =
        ((BotTradeManager.transferableAmount(bot, 995).toLong() + offeredCoins.coerceAtLeast(0L)).toDouble() *
        BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    fun allowance(bot: Bot, offeredCoins: Long = 0L): Int =
        minOf(live(bot)?.maximumCoins ?: 0, currentAllowance(bot, offeredCoins))

    /**
     * Exact recipe and manual allocations are excluded before food can satisfy a second personal purpose.
     */
    fun physicalPieces(bot: Bot, offered: Map<Int, Int> = emptyMap()): Long {
        val request = live(bot) ?: return 0L
        return foods.asSequence().filter { compatible(request, it) }.sumOf { id ->
            (bot.totalItemAmount(id).toLong() + (offered[id] ?: 0) -
                BotTradeIntentions.literalSupplyTarget(bot, id)).coerceAtLeast(0L)
        }
    }

    fun pendingPieces(bot: Bot): Long = 0L

    fun alternatives(bot: Bot): Set<Int> = foods.filterTo(linkedSetOf()) { handles(bot, it) }

    fun deficit(bot: Bot, subtractPending: Boolean = true): Int =
        (reserve(bot).toLong() - physicalPieces(bot) - if (subtractPending) pendingPieces(bot) else 0L)
            .coerceAtLeast(0L).toInt()

    fun hasOpenBuy(bot: Bot): Boolean = false

    /**
     * Native portion traversal distinguishes a full pie, half pie and cake slice without modifying the items.
     */
    internal fun remainingHealing(id: Int): Int {
        val food = Food.ID_TO_FOOD[id] ?: return 0
        var next: Int? = id
        var bites = 0
        while (next != null && bites < 4) { bites++; next = food.getNextId(next) }
        return food.heal * bites
    }

    fun demand(bot: Bot, id: Int, subtractPending: Boolean = true): WantedItemDefinition? {
        val request = live(bot) ?: return null
        if (!handles(bot, id) || !bot.canUseWantedItem(id)) return null
        val missing = deficit(bot, subtractPending)
        if (missing < 1) return null
        val owned = bot.totalItemAmount(id)
        val target = (owned.toLong() + missing).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val minimum = minOf(target, (owned.toLong() +
            (request.minimum - physicalPieces(bot)).coerceAtLeast(0L)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return WantedItemDefinition(id, minimum, target, -1, -1,
            if (physicalPieces(bot) < request.minimum) WantedItemPriority.URGENT else WantedItemPriority.STANDARD,
            WantedItemDefinition.DEFAULT_TAGS)
    }

    private fun ordinary(bot: Bot, request: Request, id: Int, strategy: BotTradePrice): Long =
        request.prices.getOrPut(id) {
            val guide = bot.world.economy.getPrice(id)
            if (guide < 1L) 0L else BotMarketExperience.buy(bot, id, strategy.applyBuy(guide).coerceAtLeast(1L))
                .coerceAtMost(Int.MAX_VALUE.toLong())
        }

    /**
     * Caps a concrete basket by a current affordable native source only when it can supply that whole basket.
     */
    fun priceCeiling(bot: Bot, id: Int, quantity: Int = 1, refundableCoins: Long = 0L): Long {
        val request = live(bot) ?: return Long.MAX_VALUE
        if (!handles(bot, id)) return Long.MAX_VALUE
        val original = ordinary(bot, request, id, BotTradePrice.EXACT)
        if (original < 1L) return 0L
        val normal = if (physicalPieces(bot) < request.minimum) minOf(Int.MAX_VALUE.toLong(), original * 10L) else original
        val funds = allowance(bot, refundableCoins).toLong()
        val source = if (quantity in 1..BotSupplySources.MAX_QUANTITY && receivingCapacity(bot, id) >= quantity)
            BotSupplySources.quote(bot, id, quantity)?.takeIf { it.quantity >= quantity && it.totalCoins <= funds }
        else null
        return minOf(normal, funds, source?.maximumUnitPrice ?: Long.MAX_VALUE)
    }

    private fun sources(bot: Bot, request: Request, need: Int): List<Basket> {
        val ids = alternatives(bot).filter { bot.canUseWantedItem(it) }
        val shops = ids.mapNotNull { id ->
            BotSupplySources.quote(bot, id, minOf(need, request.minimum).coerceAtLeast(1))
                ?.let { Basket(id, it.quantity, it.maximumUnitPrice) }
        }
        return shops + TradeBotScript.foodSupply(bot, ids.toSet())
    }

    /**
     * Chooses one real affordable food basket; an empty book permits only the stable preferred funded bid.
     */
    fun purchases(bot: Bot, strategy: BotTradePrice, purpose: BotTradePurpose, coins: Long,
                  capacity: Int = Int.MAX_VALUE): List<BotTradeItem> {
        val request = live(bot) ?: return emptyList()
        val need = deficit(bot)
        if (need < 1 || hasOpenBuy(bot)) return emptyList()
        val funds = minOf(coins, allowance(bot).toLong())
        val options = sources(bot, request, need).mapNotNull { source ->
            val original = ordinary(bot, request, source.id, strategy)
            if (original < 1L || source.unitPrice > priceCeiling(bot, source.id, minOf(source.quantity, need))) null
            else source.copy(quantity = minOf(source.quantity, receivingCapacity(bot, source.id)))
        }
        val selected = selectBasket(need, funds, capacity, options, request.preferred,
            bot.username.orEmpty().hashCode()) ?: run {
            val id = request.preferred
            if (!handles(bot, id) || !ItemDefinition.ALL.get(id).map { it.isTradeable }.orElse(false)) return@run null
            val price = ordinary(bot, request, id, strategy)
            if (price < 1L) null else selectBasket(need, funds, capacity,
                listOf(Basket(id, receivingCapacity(bot, id), price)), id, 0)
        } ?: return emptyList()
        return listOf(BotTradeItem(Item(selected.id, selected.quantity), selected.unitPrice, purpose, bot))
    }

    internal fun selectBasket(need: Int, coins: Long, capacity: Int, options: List<Basket>,
                              preferred: Int, seed: Int): Basket? {
        if (need < 1 || coins < 1L || capacity < 1) return null
        val feasible = options.mapNotNull { option ->
            if (remainingHealing(option.id) < 1 || option.unitPrice !in 1..Int.MAX_VALUE.toLong()) return@mapNotNull null
            val count = minOf(need.toLong(), option.quantity.toLong(), capacity.toLong(), coins / option.unitPrice,
                Int.MAX_VALUE.toLong() / option.unitPrice).toInt()
            option.copy(quantity = count).takeIf { count > 0 }
        }
        if (feasible.isEmpty()) return null
        val complete = feasible.filter { it.quantity >= minOf(need, 28) }.ifEmpty { feasible }
        val bestValue = complete.minOf { it.unitPrice.toDouble() / remainingHealing(it.id) }
        val similar = complete.filter { it.unitPrice.toDouble() / remainingHealing(it.id) <= bestValue * 1.25 }
            .sortedWith(compareBy<Basket> { it.id }.thenBy { it.unitPrice })
        return similar.firstOrNull { it.id == preferred } ?: similar[Math.floorMod(seed, similar.size)]
    }

    /**
     * Exposes only one concrete goal to personal production, without expanding all substitute recipes.
     */
    fun selectedId(bot: Bot): Int? {
        val request = live(bot) ?: return null
        if (deficit(bot) < 1) return null
        val value = bot.attributes()[state.attr]
        val now = System.currentTimeMillis()
        if (value.selectedUntil <= now || value.revision != bot.itemOwnershipRevision) {
            value.selected = purchases(bot, BotTradePrice.EXACT, BotTradePurpose.BUY, allowance(bot).toLong())
                .firstOrNull()?.item?.id ?: request.preferred
            value.selectedUntil = now + 5_000L
            value.revision = bot.itemOwnershipRevision
        }
        return value.selected.takeIf { handles(bot, it) }
    }

    fun constrain(quote: BotTradeItem, offeredCoins: Long = 0L): BotTradeItem? {
        val id = quote.item.id
        if (id !in Food.ID_TO_FOOD || BotTradeIntentions.hasLiteralSupplyDemand(quote.bot, id)) return quote
        if (!active(quote.bot)) return if (quote.purpose == BotTradePurpose.MERCHANT_BUY) quote else null
        if (!handles(quote.bot, id) || hasOpenBuy(quote.bot)) return null
        if (quote.price !in 1L..Int.MAX_VALUE.toLong()) return null
        val count = minOf(quote.item.amount.toLong(), deficit(quote.bot).toLong(),
            receivingCapacity(quote.bot, id).toLong(), allowance(quote.bot, offeredCoins).toLong() / quote.price).toInt()
        return if (count < 1 || quote.price > priceCeiling(quote.bot, id, count, offeredCoins)) null else
            BotTradeItem(Item(id, count), quote.price, quote.purpose, quote.bot)
    }

    fun allows(bot: Bot, receiving: Map<Int, Int>): Boolean {
        val foods = receiving.filterKeys { it in Food.ID_TO_FOOD && !BotTradeIntentions.hasLiteralSupplyDemand(bot, it) }
        if (foods.isEmpty()) return true
        if (!active(bot)) return bot.preferences.isMerchant
        return !hasOpenBuy(bot) && foods.all { (id, amount) -> handles(bot, id) && amount > 0 } &&
            foods.values.sumOf { it.toLong() } <= deficit(bot)
    }

    fun wouldRetain(bot: Bot, id: Int): Boolean = compatible(bot, id) && physicalPieces(bot) < reserve(bot)

    fun retainedStock(bot: Bot): Map<Int, Int> = buildMap {
        var remaining = reserve(bot)
        for (id in foods.filter { compatible(bot, it) }.sortedByDescending(::remainingHealing)) {
            if (remaining <= 0) break
            val literal = BotTradeIntentions.literalSupplyTarget(bot, id)
            val held = minOf((bot.totalItemAmount(id) - literal).coerceAtLeast(0), remaining)
            if (held > 0) put(id, literal + held)
            remaining -= held
        }
    }

    fun retained(bot: Bot, id: Int): Int = if (compatible(bot, id)) retainedStock(bot)[id] ?: 0 else 0

    fun outgoingAllowed(bot: Bot, offered: Map<Int, Int>): Boolean =
        offered.keys.none { compatible(bot, it) } || physicalPieces(bot) >= minOf(reserve(bot).toLong(), physicalPieces(bot, offered))

    fun saleableLimit(bot: Bot, id: Int, offered: Map<Int, Int> = emptyMap()): Long =
        if (!compatible(bot, id)) Long.MAX_VALUE else
            (physicalPieces(bot, offered) - reserve(bot)).coerceAtLeast(0L)

    private fun receivingCapacity(bot: Bot, id: Int): Int {
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return 0
        val delivery = definition.notedId.orElse(id)
        val space = bot.inventory.computeRemainingSize()
        return if ((definition.isStackable || delivery != id) &&
            (space > 0 || bot.inventory.computeAmountForId(delivery) > 0)) Int.MAX_VALUE else space
    }

    private fun choose(options: List<Food>, seed: Int): Food {
        val ordered = options.sortedBy { it.id }
        var mixed = seed xor (seed ushr 16)
        mixed *= 0x7feb352d
        mixed = mixed xor (mixed ushr 15)
        mixed *= 0x846ca68b.toInt()
        mixed = mixed xor (mixed ushr 16)
        var roll = Math.floorMod(mixed, ordered.sumOf { weights.getValue(it) })
        for (food in ordered) { roll -= weights.getValue(food); if (roll < 0) return food }
        return ordered.last()
    }
}
