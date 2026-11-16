package api.bot.action.trading

import api.attr.Attr
import api.predef.ext.canUseWantedItem
import api.predef.ext.totalItemAmount
import game.player.item.consume.potion.Potion
import game.player.item.consume.potion.PotionFamily
import game.skill.herblore.makePotion.FinishedPotion
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.mob.bot.Bot

/**
 * Keeps personal activity potion needs in doses while ordinary trades and explicit wishes keep exact item ids.
 * Only the four reviewed variants of a requested family are inspected when preparing a purchase.
 *
 * @author lare96
 */
object BotPotionDemand {
    /**
     * A bounded personal reserve, independent of a merchant's finite production inputs.
     *
     * @author lare96
     */
    private data class Request(val expires: Long, val buying: Boolean, val minimum: Int = 4, val target: Int = 12,
                               val originalPrices: MutableMap<Int, Long> = HashMap())

    /**
     * An executable or resting exact-id purchase basket.
     *
     * @author lare96
     */
    internal data class Basket(val id: Int, val doses: Int, val quantity: Int, val unitPrice: Long) {
        val coins: Long get() = quantity.toLong() * unitPrice
    }

    private val requests = Attr.map<Int, Request>()
    private val brewed by lazy(LazyThreadSafetyMode.NONE) { FinishedPotion.entries.mapTo(HashSet()) { it.id } }

    /**
     * Preserves the existing zero-stock trigger; an existing request follows remaining doses after drinking.
     */
    fun request(bot: Bot, potion: Potion, now: Long = System.currentTimeMillis()) {
        val family = PotionFamily.forPotion(potion)
        val current = live(bot, now)
        if (family.ids.none { ItemDefinition.ALL.get(it).map { definition -> definition.isTradeable }.orElse(false) && bot.canUseWantedItem(it) }) return
        val horizon = if (bot.personality.isDextrous || bot.personality.isIntelligent) 7_200_000L else 1_800_000L
        val previous = current[family.fourDose]
        val buying = previous?.buying == true || physicalDoses(bot, family) == 0L
        current[family.fourDose] = Request(now + horizon, buying,
            originalPrices = previous?.originalPrices ?: HashMap())
    }

    /**
     * Enumerates only requested personal families, without scanning the population or item catalogue.
     */
    fun requestedPotionFamilies(bot: Bot, now: Long = System.currentTimeMillis()): Set<PotionFamily> =
        live(bot, now).keys.mapNotNullTo(linkedSetOf()) { PotionFamily.forId(it) }

    /**
     * Reads the personal reserve without including the active finite production job itself.
     */
    fun reserveDoses(bot: Bot, family: PotionFamily, now: Long = System.currentTimeMillis()): Int =
        live(bot, now)[family.fourDose]?.let { if (it.buying) it.target
            else minOf(it.target.toLong(), physicalDoses(bot, family)).toInt() } ?: 0

    fun physicalDoses(bot: Bot, family: PotionFamily): Long =
        family.ids.sumOf { bot.totalItemAmount(it).toLong() * family.doses(it) }

    fun pendingDoses(bot: Bot, family: PotionFamily): Long = 0L

    /**
     * Counts current physical ownership; ordinary player trades have no separate durable delivery ledger.
     */
    fun deficitDoses(bot: Bot, family: PotionFamily, now: Long = System.currentTimeMillis()): Int =
        (reserveDoses(bot, family, now).toLong() - physicalDoses(bot, family) - pendingDoses(bot, family))
            .coerceAtLeast(0L).toInt()

    /**
     * Converts the outstanding family need to one concrete offered variant without broad default targets.
     */
    fun demand(bot: Bot, id: Int, subtractPending: Boolean = true,
               now: Long = System.currentTimeMillis()): WantedItemDefinition? {
        val family = PotionFamily.forId(id) ?: return null
        val request = live(bot, now)[family.fourDose] ?: return null
        if (!request.buying) return null
        if (!bot.canUseWantedItem(id)) return null
        val physical = physicalDoses(bot, family)
        val missing = (request.target - physical - if (subtractPending) pendingDoses(bot, family) else 0L)
            .coerceAtLeast(0L)
        if (missing < 1L) return null
        val doses = family.doses(id)
        if (doses < 1) return null
        val owned = bot.totalItemAmount(id)
        val target = (owned.toLong() + divideUp(missing, doses)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val minimum = (owned.toLong() + divideUp((request.minimum - physical).coerceAtLeast(0L), doses))
            .coerceAtMost(target.toLong()).toInt()
        return WantedItemDefinition(id, minimum, target, -1, -1,
            if (physical < request.minimum) WantedItemPriority.URGENT else WantedItemPriority.STANDARD,
            WantedItemDefinition.DEFAULT_TAGS)
    }

    /**
     * Selects one exact variant per family. A native three-dose advertisement remains possible without a prepared seller.
     * The ordinary trade confirmation rechecks every ownership and payment limit.
     */
    fun purchases(bot: Bot, strategy: BotTradePrice, purpose: BotTradePurpose, coins: Long,
                  capacity: Int = Int.MAX_VALUE, now: Long = System.currentTimeMillis()): List<BotTradeItem> {
        var remaining = coins.coerceAtLeast(0L)
        val result = ArrayList<BotTradeItem>()
        for (family in requestedPotionFamilies(bot, now)) {
            val request = live(bot, now).getValue(family.fourDose)
            val need = deficitDoses(bot, family, now)
            if (need < 1) continue
            val options = TradeBotScript.potionSupply(bot, family).mapNotNull { offer ->
                val guide = bot.world.economy.getPrice(offer.id)
                if (guide < 1L || !bot.canUseWantedItem(offer.id)) return@mapNotNull null
                val ordinary = request.originalPrices.getOrPut(offer.id) {
                    BotMarketExperience.buy(bot, offer.id, strategy.applyBuy(guide).coerceAtLeast(1L))
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                }
                if (offer.unitPrice > ordinary) null else offer.copy(quantity = minOf(offer.quantity,
                    capacity, receivingCapacity(bot, offer.id)))
            }
            val selected = selectBasket(need, remaining, capacity, options) ?: run {
                val id = family.threeDose.takeIf { it in brewed } ?: family.fourDose
                if (!ItemDefinition.ALL.get(id).map { it.isTradeable }.orElse(false) || !bot.canUseWantedItem(id)) return@run null
                val guide = bot.world.economy.getPrice(id)
                if (guide < 1L) return@run null
                val price = request.originalPrices.getOrPut(id) {
                    BotMarketExperience.buy(bot, id, strategy.applyBuy(guide).coerceAtLeast(1L))
                        .coerceAtMost(Int.MAX_VALUE.toLong())
                }
                selectBasket(need, remaining, capacity,
                    listOf(Basket(id, family.doses(id), receivingCapacity(bot, id), price)))
            } ?: continue
            result += BotTradeItem(Item(selected.id, selected.quantity), selected.unitPrice, purpose, bot)
            remaining -= selected.coins
        }
        return result
    }

    /**
     * Prefers the cheapest complete rounded basket; if none fits, buys useful affordable doses with bounded stock.
     */
    internal fun selectBasket(need: Int, coins: Long, capacity: Int, options: List<Basket>): Basket? {
        if (need < 1 || coins < 1 || capacity < 1) return null
        val feasible = options.mapNotNull { option ->
            if (option.doses !in 1..4 || option.unitPrice !in 1..Int.MAX_VALUE.toLong()) return@mapNotNull null
            val count = minOf(divideUp(need.toLong(), option.doses), option.quantity.toLong(),
                coins / option.unitPrice, capacity.toLong(), Int.MAX_VALUE.toLong() / option.unitPrice).toInt()
            option.copy(quantity = count).takeIf { count > 0 }
        }
        val complete = feasible.filter { it.quantity.toLong() * it.doses >= need }
        return if (complete.isNotEmpty()) complete.minWithOrNull(compareBy<Basket> { it.coins }
            .thenBy { it.unitPrice.toDouble() / it.doses }.thenBy { it.quantity }.thenBy { it.id })
        else feasible.minWithOrNull(compareBy<Basket> { it.unitPrice.toDouble() / it.doses }
            .thenByDescending { it.quantity * it.doses }.thenBy { it.coins }.thenBy { it.id })
    }

    /**
     * Revalidates stale concrete personal quotes against every current family variant and delivery.
     */
    fun constrain(quote: BotTradeItem): BotTradeItem? {
        val family = PotionFamily.forId(quote.item.id) ?: return quote
        if (reserveDoses(quote.bot, family) < 1 ||
            BotTradeIntentions.hasExactSupplyDemand(quote.bot, quote.item.id)) return quote
        val maximum = divideUp(deficitDoses(quote.bot, family).toLong(), family.doses(quote.item.id)).toInt()
        val count = minOf(quote.item.amount, maximum)
        val original = live(quote.bot, System.currentTimeMillis())[family.fourDose]?.originalPrices
            ?.getOrPut(quote.item.id) { quote.price.coerceIn(1L, Int.MAX_VALUE.toLong()) } ?: quote.price
        if (quote.price > minOf(Int.MAX_VALUE.toLong(), original)) return null
        return if (count < 1) null else BotTradeItem(Item(quote.item.id, count), quote.price, quote.purpose, quote.bot)
    }

    /**
     * Rejects a stale or multi-variant personal basket before either ordinary trade confirmation.
     * Literal wishes and exact finite-production ingredients retain their own guards.
     */
    fun allows(bot: Bot, receiving: Map<Int, Int>): Boolean {
        val grouped = receiving.entries.filter { (id, _) ->
            PotionFamily.forId(id)?.let { reserveDoses(bot, it) > 0 } == true &&
                !BotTradeIntentions.hasExactSupplyDemand(bot, id)
        }.groupBy { PotionFamily.forId(it.key)!! }
        return grouped.all { (family, entries) ->
            if (entries.size != 1) false else {
                val entry = entries.single()
                val missing = deficitDoses(bot, family)
                entry.value > 0 && entry.value.toLong() <= divideUp(missing.toLong(), family.doses(entry.key))
            }
        }
    }

    private fun receivingCapacity(bot: Bot, id: Int): Int {
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return 0
        val delivery = definition.notedId.orElse(id)
        val stacked = definition.isStackable || delivery != id
        val space = bot.inventory.computeRemainingSize()
        return if (stacked && (space > 0 || bot.inventory.computeAmountForId(delivery) > 0)) Int.MAX_VALUE else space
    }

    /**
     * Allocates the shared dose reserve to actual bottles, retaining useful partials after an id-changing sip.
     */
    fun retainedStock(bot: Bot): Map<Int, Int> = buildMap {
        for (family in requestedPotionFamilies(bot)) {
            var remaining = reserveDoses(bot, family)
            for (id in family.ids.sortedByDescending { family.doses(it) }) {
                if (remaining <= 0) break
                val doses = family.doses(id)
                val held = minOf(bot.totalItemAmount(id), divideUp(remaining.toLong(), doses).toInt())
                if (held > 0) put(id, held)
                remaining -= held * doses
            }
        }
    }

    private fun divideUp(value: Long, doses: Int): Long = if (value <= 0L || doses < 1) 0L else (value + doses - 1L) / doses

    private fun live(bot: Bot, now: Long): HashMap<Int, Request> = bot.attributes()[requests.attr].also {
        it.entries.removeIf { entry -> entry.value.expires <= now }
    }
}
