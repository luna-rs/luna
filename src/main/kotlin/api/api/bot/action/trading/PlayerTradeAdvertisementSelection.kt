package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import api.bot.action.trading.PlayerTradeAdvertisementPayment.Plan
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Quote
import io.luna.game.model.mob.bot.Bot
import java.math.BigInteger
import java.util.IdentityHashMap

/**
 * Chooses one prepared buyer for the same advertised commodity and terms.
 *
 * Buyers accepting the asking price precede counteroffers and compete by purchase quantity. Counteroffers compete by
 * exact unit-equivalent quote, then quantity. Repeated candidates for one bot instance retain only its best option;
 * one supplied sample chooses among the remaining exact ties in first-appearance order.
 *
 * Visibility, available stock, privacy and advertisement ownership remain the caller's responsibility.
 *
 * @author lare96
 */
internal object PlayerTradeAdvertisementSelection {

    /**
     * One bot's already prepared quote and feasible payment for an ordinary, resolved advertisement.
     *
     * @author lare96
     */
    data class Candidate(val buyer: Bot, val offer: PlayerTradeAdvertisement, val quote: Quote, val plan: Plan) {
        init {
            val commodity = offer.itemIds.singleOrNull()
            require(commodity != null && commodity >= 0 && commodity != 995 && !offer.requiresInspection)
            require(quote.price.basis != PriceBasis.UNCLEAR)
            require(quote.price.basis != PriceBasis.TOTAL || offer.quantity != null)
            require(plan.quantity > 0 && plan.cost > 0 && plan.paidValue >= plan.cost)
            require(offer.quantity == null || plan.quantity <= offer.quantity)
            require(plan.payment.isNotEmpty() && plan.payment.values.all { it > 0 })
        }
    }

    /**
     * Selects one candidate using an explicit random sample, without claiming an advertisement or starting a trade.
     * Every candidate must describe equal advertised terms; unrelated items and differently priced lots are separate
     * selection calls. Sampling one chooses the final bucket, while other samples use their zero-based bucket.
     */
    fun choose(candidates: List<Candidate>, tieSample: Double): Candidate? {
        require(tieSample.isFinite() && tieSample in 0.0..1.0)
        if (candidates.isEmpty()) return null
        val advertised = candidates.first().offer
        require(candidates.all { it.offer == advertised })

        val positions = IdentityHashMap<Bot, Int>()
        val unique = ArrayList<Candidate>()
        for (candidate in candidates) {
            val previous = positions[candidate.buyer]
            if (previous == null) {
                positions[candidate.buyer] = unique.size
                unique += candidate
            } else if (compare(candidate, unique[previous]) > 0) {
                unique[previous] = candidate
            }
        }

        var best = unique.first()
        for (candidate in unique.drop(1)) {
            if (compare(candidate, best) > 0) best = candidate
        }
        val tied = unique.filter { compare(it, best) == 0 }
        val bucket = (tieSample * tied.size).toInt().coerceAtMost(tied.lastIndex)
        return tied[bucket]
    }

    /**
     * Orders purchase candidates without treating rounded partial payments as their quoted unit prices.
     */
    private fun compare(left: Candidate, right: Candidate): Int {
        if (left.quote.counteroffer != right.quote.counteroffer) return if (left.quote.counteroffer) -1 else 1
        if (left.quote.counteroffer) {
            val priceOrder = BigInteger.valueOf(left.quote.price.coins).multiply(denominator(right))
                .compareTo(BigInteger.valueOf(right.quote.price.coins).multiply(denominator(left)))
            if (priceOrder != 0) return if (left.offer.direction == PlayerTradeAdvertisement.Direction.BUY)
                -priceOrder else priceOrder
        }
        return left.plan.quantity.compareTo(right.plan.quantity)
    }

    /**
     * The number of advertised units covered by a quoted price.
     */
    private fun denominator(candidate: Candidate): BigInteger = when (candidate.quote.price.basis) {
        PriceBasis.EACH -> BigInteger.ONE
        PriceBasis.TOTAL -> BigInteger.valueOf(candidate.offer.quantity!!.toLong())
        PriceBasis.UNCLEAR -> error("A selection candidate must have a clear quote.")
    }
}
