package api.bot.action.trading

/**
 * Calculates fixed trade quantities without changing inventories or trade interfaces.
 *
 * Callers supply normalized item ids and a stable set of unit prices. Selection always starts from the original
 * available stock, rather than scaling an already reduced offer. Whole items are selected in id order and values
 * saturate at [Long.MAX_VALUE]. Unknown or non-positive prices never contribute trade value.
 *
 * @author lare96
 */
internal object TradeBasket {

    /**
     * Selects the largest affordable quantity of each available item within [budget].
     *
     * @param stock The maximum available quantity of each item.
     * @param prices The agreed price per unit of each item.
     * @param budget The maximum total value to select, in coins.
     * @return Positive quantities whose combined value does not exceed the budget.
     */
    fun select(stock: Map<Int, Int>, prices: Map<Int, Long>, budget: Long): Map<Int, Int> {
        var remaining = budget.coerceAtLeast(0)
        val selected = linkedMapOf<Int, Int>()
        for ((id, available) in stock.toSortedMap()) {
            val price = prices[id] ?: continue
            if (price <= 0 || available <= 0) continue
            val amount = minOf(available.toLong(), remaining / price).toInt()
            if (amount > 0) {
                selected[id] = amount
                remaining -= amount.toLong() * price
            }
        }
        return selected
    }

    /**
     * Calculates the total value of [items], using positive prices and saturated arithmetic.
     *
     * @param items The item quantities to value.
     * @param prices The price per unit of each item.
     * @return The combined value, or [Long.MAX_VALUE] when the total would overflow.
     */
    fun value(items: Map<Int, Int>, prices: Map<Int, Long>): Long {
        var total = 0L
        for ((id, amount) in items) {
            val price = prices[id] ?: continue
            if (price <= 0 || amount <= 0) continue
            if (price > (Long.MAX_VALUE - total) / amount) return Long.MAX_VALUE
            total += price * amount
        }
        return total
    }

    /**
     * Matches sale stock to wanted quantities at the seller's asking prices.
     *
     * A buyer must be willing to pay each asking price. Both bots use the same stock, demand, and coin limit so
     * independently preparing the exchange produces the same item quantities and payment.
     *
     * @param stock The seller's transferable stock.
     * @param demand The buyer's remaining wanted quantities.
     * @param asks The seller's unit prices.
     * @param bids The buyer's maximum unit prices.
     * @param coins The buyer's available coin payment.
     * @return The quantities the seller should offer; payment is their value at [asks].
     */
    fun match(stock: Map<Int, Int>, demand: Map<Int, Int>, asks: Map<Int, Long>,
              bids: Map<Int, Long>, coins: Long): Map<Int, Int> {
        val eligible = stock.mapValues { (id, amount) ->
            val ask = asks[id] ?: 0
            if (ask > 0 && ask <= (bids[id] ?: 0)) minOf(amount, demand[id] ?: 0) else 0
        }
        return select(eligible, asks, coins)
    }

    /**
     * Resolves both sides of an exchange paid with coins, barter items, or both.
     *
     * Coins are spent first. Indivisible payment items may overpay the asking value only within the buyer's
     * quoted price limits. When exact payment cannot be composed, sale quantities are reduced from the original
     * stock. At most twelve calculation passes are allowed; no inventory changes occur during planning.
     *
     * @param stock The seller's available stock.
     * @param demand The buyer's requested quantities.
     * @param asks The seller's minimum unit prices.
     * @param bids The buyer's maximum unit prices.
     * @param payment The buyer's available payment stock.
     * @param paymentPrices The agreed unit values of the payment items.
     * @return Seller and buyer quantities respectively, or `null` when no whole-item exchange is possible.
     */
    fun exchange(stock: Map<Int, Int>, demand: Map<Int, Int>, asks: Map<Int, Long>, bids: Map<Int, Long>,
                 payment: Map<Int, Int>, paymentPrices: Map<Int, Long>): Pair<Map<Int, Int>, Map<Int, Int>>? {
        var budget = value(payment, paymentPrices)
        repeat(12) {
            val goods = match(stock, demand, asks, bids, budget)
            if (goods.isEmpty()) return null
            // Avoid exchanging the same commodity in both directions.
            val usablePayment = payment.filterKeys { it !in goods }
            val cost = value(goods, asks)
            val limit = minOf(value(goods, bids), value(usablePayment, paymentPrices))
            val paid = cover(usablePayment, paymentPrices, cost, limit)
            val paidValue = value(paid, paymentPrices)
            if (paidValue >= cost) return goods to paid
            budget = paidValue
        }
        return null
    }

    /** Selects payment toward [cost], with coins first and no value above [limit]. */
    private fun cover(stock: Map<Int, Int>, prices: Map<Int, Long>, cost: Long, limit: Long): Map<Int, Int> {
        var paid = 0L
        val result = linkedMapOf<Int, Int>()
        for ((id, amount) in stock.entries.sortedWith(compareBy<Map.Entry<Int, Int>> { it.key != 995 }.thenBy { it.key })) {
            val price = prices[id] ?: continue
            if (amount < 1 || price < 1 || paid >= cost) continue
            val missing = cost - paid
            val needed = missing / price + if (missing % price == 0L) 0 else 1
            val take = minOf(amount.toLong(), needed, (limit - paid).coerceAtLeast(0) / price).toInt()
            if (take > 0) {
                result[id] = take
                paid += take.toLong() * price
            }
        }
        return result
    }
}
