package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Profile
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Quote
import java.math.BigInteger
import java.util.Collections

/**
 * Calculates advertised purchase costs and quantity limits without choosing or moving payment items.
 *
 * Partial bundles round their combined proportional cost up to a whole coin. The original lot ratio is retained;
 * rounding a fractional unit price first would overcharge larger deliveries. Value affordability is only an upper
 * bound: the trade must still compose an actual coin-first payment from its eligible stock and validate both screens.
 *
 * @author lare96
 */
object PlayerTradeAdvertisementPayment {

    private const val COINS = 995
    private const val MAX_PASSES = 12

    /**
     * A feasible purchase and its actual payment, valued using the captured guide prices.
     *
     * @author lare96
     */
    data class Plan(val quantity: Int, val payment: Map<Int, Int>, val cost: Long, val paidValue: Long)

    /**
     * One eligible carried payment commodity, using normalized ids and a fixed positive guide value.
     *
     * @author lare96
     */
    private data class PaymentItem(val id: Int, val amount: Int, val value: Long)

    /**
     * Composes a coin-first payment from already eligible, budgeted stock. Item payments may exceed the agreed cost
     * within the personality's barter allowance; that allowance never raises the quoted price or coin-only payment.
     *
     * Each calculation starts from the original stock. Deterministic item orders explore feasible baskets without
     * exhaustive subset search, and failed compositions reduce the quantity with a bounded stopping condition.
     * A coin-only purchase remains available if the larger barter purchase cannot be composed.
     *
     * @param maximumUnitPrice The bot's normal cash valuation, before the extra item-payment allowance.
     * @param wanted Remaining demand, also capped by any separately tracked remaining advertised stock.
     * @param paymentStock Actual transferable quantities, normalized to unnoted ids and limited to spendable stock.
     * @param paymentPrices Positive guide values captured for this trade. Coins always have value one.
     * @param spendingLimit A hard value limit, such as the visit's remaining curiosity allowance.
     */
    fun plan(offer: PlayerTradeAdvertisement, quote: Quote, maximumUnitPrice: Long, profile: Profile,
             wanted: Int, paymentStock: Map<Int, Int>, paymentPrices: Map<Int, Long>,
             spendingLimit: Long = Long.MAX_VALUE): Plan? {
        val commodity = offer.itemIds.singleOrNull() ?: return null
        val units = units(offer, quote) ?: return null
        if (commodity < 0 || commodity == COINS || maximumUnitPrice <= 0 || wanted <= 0 || spendingLimit <= 0) return null
        if (BigInteger.valueOf(quote.price.coins) > BigInteger.valueOf(maximumUnitPrice)
                .multiply(BigInteger.valueOf(units.toLong()))) return null

        val coins = minOf((paymentStock[COINS] ?: 0).coerceAtLeast(0).toLong(), spendingLimit).toInt()
        val items = paymentStock.mapNotNull { (id, amount) ->
            val value = paymentPrices[id] ?: return@mapNotNull null
            if (id < 0 || id == COINS || id == commodity || amount <= 0 || value <= 0) null
            else PaymentItem(id, amount, value)
        }.sortedWith(compareBy<PaymentItem> { it.value }.thenBy { it.id })
        val value = items.fold(BigInteger.valueOf(coins.toLong())) { total, item ->
            total.add(BigInteger.valueOf(item.value).multiply(BigInteger.valueOf(item.amount.toLong())))
        }
        var budget = value.min(BigInteger.valueOf(spendingLimit)).longValueExact()
        val coinQuantity = affordableQuantity(offer, quote, wanted, coins.toLong())
        val coinPlan = if (coinQuantity == 0) null else cost(offer, quote, coinQuantity)?.let {
            Plan(coinQuantity, mapOf(COINS to it.toInt()), it, it)
        }
        repeat(MAX_PASSES) {
            val quantity = affordableQuantity(offer, quote, wanted, budget)
            if (quantity <= coinQuantity) return coinPlan
            val cost = cost(offer, quote, quantity) ?: return coinPlan
            val normalLimit = BigInteger.valueOf(maximumUnitPrice).multiply(BigInteger.valueOf(quantity.toLong()))
                .min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact()
            val ceiling = minOf(PlayerTradeAdvertisementPolicy.barterLimit(normalLimit, profile), spendingLimit)
            val (payment, paid) = compose(cost, coins, items, ceiling)
            if (payment != null) return Plan(quantity, Collections.unmodifiableMap(payment), cost, paid)
            if (paid >= budget) return coinPlan
            budget = paid
        }
        return coinPlan
    }

    /**
     * Tries each item type first, preferring lower total payment and then more coins among the feasible results.
     * Cash is chosen after the indivisible items so their excess can replace coins rather than create overpayment.
     * The second result is either the complete payment value or a strictly smaller budget for the next quantity.
     */
    private fun compose(cost: Long, coins: Int, items: List<PaymentItem>, ceiling: Long): Pair<Map<Int, Int>?, Long> {
        var best: Map<Int, Int>? = null
        var bestValue = Long.MAX_VALUE
        var bestCoins = -1
        var partial = coins.toLong()
        val shortfall = cost - coins
        for (first in 0 until maxOf(1, items.size)) {
            val order = if (first == 0) items else listOf(items[first]) + items.filterIndexed { index, _ -> index != first }
            val selected = LinkedHashMap<Int, Int>()
            var barter = 0L
            for (item in order) {
                val missing = shortfall - barter
                if (missing <= 0) break
                val needed = 1 + (missing - 1) / item.value
                val take = minOf(item.amount.toLong(), needed, (ceiling - barter) / item.value).toInt()
                if (take > 0) {
                    selected[item.id] = take
                    // The preceding ceiling division proves this product and addition are representable.
                    barter += take.toLong() * item.value
                }
            }
            val cash = minOf(coins.toLong(), (cost - barter).coerceAtLeast(0)).toInt()
            if (cash > 0) selected[COINS] = cash
            val paid = barter + cash
            if (paid >= cost) {
                if (best == null || paid < bestValue || paid == bestValue && cash > bestCoins) {
                    best = selected
                    bestValue = paid
                    bestCoins = cash
                }
            } else partial = maxOf(partial, paid)
        }
        return if (best != null) best to bestValue else null to partial
    }

    /**
     * Computes the exact unit-price payment or the rounded-up proportional bundle payment.
     *
     * @return Whole-GP value owed, or `null` for invalid terms, excess quantities or an unrepresentable total.
     */
    fun cost(offer: PlayerTradeAdvertisement, quote: Quote, quantity: Int): Long? {
        val units = units(offer, quote) ?: return null
        if (quantity < 0 || offer.quantity?.let { quantity > it } == true) return null
        val numerator = BigInteger.valueOf(quote.price.coins).multiply(BigInteger.valueOf(quantity.toLong()))
        val divided = numerator.divideAndRemainder(BigInteger.valueOf(units.toLong()))
        val total = divided[0].add(if (divided[1].signum() > 0) BigInteger.ONE else BigInteger.ZERO)
        return if (total > BigInteger.valueOf(Long.MAX_VALUE)) null else total.longValueExact()
    }

    /**
     * Finds the largest affordable whole-item quantity within wanted stock and the advertisement's stock limit.
     * This does not imply that an indivisible barter payment can actually cover that quantity.
     *
     * @param budget The maximum eligible payment value, with actual coin and barter composition checked separately.
     */
    fun affordableQuantity(offer: PlayerTradeAdvertisement, quote: Quote, wanted: Int, budget: Long): Int {
        if (wanted <= 0 || budget <= 0) return 0
        val units = units(offer, quote) ?: return 0
        val limit = minOf(wanted, offer.quantity ?: wanted)
        // ceil(quantity * coins / units) <= budget exactly when quantity * coins <= budget * units.
        val affordable = BigInteger.valueOf(budget).multiply(BigInteger.valueOf(units.toLong()))
            .divide(BigInteger.valueOf(quote.price.coins))
        return affordable.min(BigInteger.valueOf(limit.toLong())).toInt()
    }

    /**
     * Keeps explicit unit quotes and complete lot ratios separate from unclear prices.
     */
    private fun units(offer: PlayerTradeAdvertisement, quote: Quote): Int? = when (quote.price.basis) {
        PriceBasis.EACH -> 1
        PriceBasis.TOTAL -> offer.quantity
        PriceBasis.UNCLEAR -> null
    }
}
