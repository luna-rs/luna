package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.Price
import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * Computes marketplace curiosity and opening purchase quotes from captured personality and explicit random samples.
 *
 * The marketplace samples curiosity once per visit and retains each advertisement's quote. This policy does not draw
 * random numbers, change inventories or send messages. Total prices retain their advertised lot size, so fractional
 * unit prices are never rounded before the eventual purchased quantity is known.
 *
 * @author lare96
 */
object PlayerTradeAdvertisementPolicy {

    /**
     * Existing personality and merchanting preferences captured for a visit or an advertisement decision.
     * Finite values outside the normal trait range are bounded when computing probabilities.
     *
     * @author lare96
     */
    data class Profile(val intelligence: Double = 0.5,
                       val kindness: Double = 0.5,
                       val confidence: Double = 0.5,
                       val social: Double = 0.5,
                       val merchantingInterest: Double = 0.5,
                       val merchant: Boolean = false,
                       val greedy: Boolean = false) {
        init {
            require(listOf(intelligence, kindness, confidence, social, merchantingInterest).all { it.isFinite() })
        }
    }

    /**
     * A stable quote in the same price basis as the advertisement, or a normal unit bid for an unpriced sale.
     * Counteroffers and native trade requests follow the recipient's Trade/compete setting.
     *
     * @author lare96
     */
    data class Quote(val price: Price, val counteroffer: Boolean)

    /**
     * Gives an average visitor a five-percent browsing chance, rising to twenty percent for the keenest profiles.
     */
    fun curiosityChance(profile: Profile): Double {
        val interest = profile.confidence.coerceIn(0.0, 1.0) * 0.40 +
                profile.social.coerceIn(0.0, 1.0) * 0.35 +
                profile.merchantingInterest.coerceIn(0.0, 1.0) * 0.25
        return 0.20 * interest * interest
    }

    /**
     * Decides a visit's browsing mood without rerolling it in response to repeated advertisements.
     */
    fun curious(profile: Profile, sample: Double): Boolean {
        requireSample(sample)
        return sample < curiosityChance(profile)
    }

    /**
     * Starts ordinary buyers near fifteen percent and merchants or greedy buyers near fifty percent.
     * Confidence and judgment encourage bargaining, while kindness makes accepting the asking price more likely.
     */
    fun haggleChance(profile: Profile): Double {
        val baseline = if (profile.merchant || profile.greedy) 0.50 else 0.15
        val adjustment = (profile.confidence.coerceIn(0.0, 1.0) - 0.5) * 0.10 +
                (profile.intelligence.coerceIn(0.0, 1.0) - 0.5) * 0.10 -
                (profile.kindness.coerceIn(0.0, 1.0) - 0.5) * 0.10
        return (baseline + adjustment).coerceIn(0.0, 1.0)
    }

    /**
     * Raises the value ceiling for item payments by ten to fifty percent, with twenty-five percent for average
     * judgment. Callers apply this only to barter and still cap payment by eligible, spendable stock.
     */
    fun barterLimit(cashLimit: Long, profile: Profile): Long {
        if (cashLimit <= 0) return 0
        val careless = BigDecimal.ONE.subtract(BigDecimal.valueOf(profile.intelligence.coerceIn(0.0, 1.0)))
        val step = BigDecimal.valueOf(2L, 1)
        val premium = BigDecimal.valueOf(1L, 1).add(step.multiply(careless))
            .add(step.multiply(careless).multiply(careless))
        val limit = BigDecimal.valueOf(cashLimit).multiply(BigDecimal.ONE.add(premium))
            .setScale(0, RoundingMode.FLOOR)
        return limit.min(BigDecimal.valueOf(Long.MAX_VALUE)).longValueExact()
    }

    /**
     * Accepts explicit affordable terms or computes one lower counteroffer without exceeding the bot's valuation.
     * An asking price above the limit is countered at the normal buying limit. An affordable ask may be discounted by
     * five to ten percent, or ten to twenty-five percent for merchants and greedy buyers. Whole-coin counteroffers
     * round upward so they never exceed the sampled discount. A one-coin offer cannot be haggled below one coin.
     *
     * @param maximumUnitPrice The bot's captured normal buying price for this exact commodity.
     * @return A quote, or `null` for an unavailable commodity valuation or terms needing broad-buyer inspection.
     */
    fun quote(offer: PlayerTradeAdvertisement, maximumUnitPrice: Long, profile: Profile,
              haggleRoll: Double, discountRoll: Double): Quote? {
        requireSample(haggleRoll)
        requireSample(discountRoll)
        if (maximumUnitPrice <= 0 || offer.requiresInspection) return null
        val asking = offer.price ?: return Quote(Price(maximumUnitPrice, PriceBasis.EACH), false)
        val ceiling = when (asking.basis) {
            PriceBasis.EACH -> BigInteger.valueOf(maximumUnitPrice)
            PriceBasis.TOTAL -> BigInteger.valueOf(maximumUnitPrice)
                .multiply(BigInteger.valueOf((offer.quantity ?: return null).toLong()))
            PriceBasis.UNCLEAR -> return null
        }
        if (BigInteger.valueOf(asking.coins) > ceiling) {
            // This branch proves the positive ceiling fits below a representable advertised amount.
            return Quote(Price(ceiling.longValueExact(), asking.basis), true)
        }
        if (haggleRoll >= haggleChance(profile)) return Quote(asking, false)
        val bargain = profile.merchant || profile.greedy
        val minimum = BigDecimal.valueOf(if (bargain) 10L else 5L, 2)
        val spread = BigDecimal.valueOf(if (bargain) 15L else 5L, 2)
        val discount = minimum.add(BigDecimal.valueOf(discountRoll).multiply(spread))
        val counter = BigDecimal.valueOf(asking.coins)
            .multiply(BigDecimal.ONE.subtract(discount))
            .setScale(0, RoundingMode.CEILING).longValueExact()
        return if (counter > 0 && counter < asking.coins) Quote(Price(counter, asking.basis), true)
            else Quote(asking, false)
    }

    /**
     * Rejects invalid injected samples instead of letting NaN silently choose a financial decision.
     */
    private fun requireSample(sample: Double) {
        require(sample.isFinite() && sample in 0.0..1.0)
    }
}
