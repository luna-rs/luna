package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.Price
import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import api.bot.action.trading.PlayerTradeAdvertisementPayment.Plan
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Quote
import api.bot.action.trading.PlayerTradeAdvertisementSelection.Candidate
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Checks buyer preference and explicit tie sampling for one advertisement without lifecycle or world state.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementSelectionTest {

    private fun offer(price: Price? = Price(100, PriceBasis.EACH), quantity: Int? = 100): PlayerTradeAdvertisement =
        PlayerTradeAdvertisement("feathers", setOf(314), quantity, price)

    private fun candidate(advertisement: PlayerTradeAdvertisement, quantity: Int,
                          price: Price = advertisement.price ?: Price(100, PriceBasis.EACH),
                          counteroffer: Boolean = false, buyer: Bot = mock(Bot::class.java)): Candidate {
        val quote = Quote(price, counteroffer)
        val cost = PlayerTradeAdvertisementPayment.cost(advertisement, quote, quantity)!!
        return Candidate(buyer, advertisement, quote, Plan(quantity, mapOf(453 to 1), cost, cost))
    }

    @Test fun noCandidatesProduceNoSelection() {
        assertNull(PlayerTradeAdvertisementSelection.choose(emptyList(), 0.0))
        assertNull(PlayerTradeAdvertisementSelection.choose(emptyList(), 1.0))
    }

    @Test fun aSinglePreparedCandidateWinsAtEitherSampleEndpoint() {
        val only = candidate(offer(), 10)

        assertSame(only, PlayerTradeAdvertisementSelection.choose(listOf(only), 0.0))
        assertSame(only, PlayerTradeAdvertisementSelection.choose(listOf(only), 1.0))
    }

    @Test fun theLargestAcceptingPurchaseWins() {
        val advertisement = offer()
        val small = candidate(advertisement, 5)
        val large = candidate(advertisement, 40)

        assertSame(large, PlayerTradeAdvertisementSelection.choose(listOf(small, large), 0.0))
        assertSame(large, PlayerTradeAdvertisementSelection.choose(listOf(large, small), 1.0))
    }

    @Test fun acceptingTheAskPrecedesEvenALargerCounterofferPurchase() {
        val advertisement = offer()
        val accepting = candidate(advertisement, 1)
        val countering = candidate(advertisement, 100, Price(99, PriceBasis.EACH), true)

        assertSame(accepting, PlayerTradeAdvertisementSelection.choose(listOf(countering, accepting), 0.0))
        assertSame(accepting, PlayerTradeAdvertisementSelection.choose(listOf(accepting, countering), 1.0))
    }

    @Test fun unpricedKnownItemsAlsoChooseTheLargestNonCounteringPurchase() {
        val advertisement = offer(price = null)
        val smallHighBid = candidate(advertisement, 5, Price(200, PriceBasis.EACH))
        val largeLowBid = candidate(advertisement, 40, Price(10, PriceBasis.EACH))

        assertSame(largeLowBid,
            PlayerTradeAdvertisementSelection.choose(listOf(smallHighBid, largeLowBid), 0.0))
    }

    @Test fun exactAcceptanceTiesUseDeterministicSampleBuckets() {
        val advertisement = offer()
        val first = candidate(advertisement, 10)
        val second = candidate(advertisement, 10)
        val tied = listOf(first, second)

        assertSame(first, PlayerTradeAdvertisementSelection.choose(tied, 0.0))
        assertSame(first, PlayerTradeAdvertisementSelection.choose(tied, 0.499))
        assertSame(second, PlayerTradeAdvertisementSelection.choose(tied, 0.5))
        assertSame(second, PlayerTradeAdvertisementSelection.choose(tied, 1.0))
    }

    @Test fun aThreeWayTieAllowsEachBucketAndClampsSampleOneToTheLastCandidate() {
        val advertisement = offer()
        val tied = List(3) { candidate(advertisement, 10) }

        assertSame(tied[0], PlayerTradeAdvertisementSelection.choose(tied, 0.0))
        assertSame(tied[1], PlayerTradeAdvertisementSelection.choose(tied, 0.34))
        assertSame(tied[2], PlayerTradeAdvertisementSelection.choose(tied, 0.99))
        assertSame(tied[2], PlayerTradeAdvertisementSelection.choose(tied, 1.0))
    }

    @Test fun theHighestCounterPriceWinsBeforePurchaseQuantity() {
        val advertisement = offer()
        val lowerLarge = candidate(advertisement, 100, Price(75, PriceBasis.EACH), true)
        val higherSmall = candidate(advertisement, 1, Price(80, PriceBasis.EACH), true)

        assertSame(higherSmall,
            PlayerTradeAdvertisementSelection.choose(listOf(lowerLarge, higherSmall), 0.0))
    }

    @Test fun equalCounterPricesPreferTheLargestPurchase() {
        val advertisement = offer()
        val small = candidate(advertisement, 1, Price(80, PriceBasis.EACH), true)
        val large = candidate(advertisement, 40, Price(80, PriceBasis.EACH), true)

        assertSame(large, PlayerTradeAdvertisementSelection.choose(listOf(small, large), 0.0))
    }

    @Test fun exactCounterPriceAndQuantityTiesUseTheSuppliedSample() {
        val advertisement = offer()
        val first = candidate(advertisement, 10, Price(80, PriceBasis.EACH), true)
        val second = candidate(advertisement, 10, Price(80, PriceBasis.EACH), true)

        assertSame(first, PlayerTradeAdvertisementSelection.choose(listOf(first, second), 0.0))
        assertSame(second, PlayerTradeAdvertisementSelection.choose(listOf(first, second), 1.0))
    }

    @Test fun comparesEachAndTotalCounterPricesAsExactUnitRatios() {
        val advertisement = offer(Price(2_000, PriceBasis.TOTAL), 100)
        val fourteenEach = candidate(advertisement, 40, Price(1_400, PriceBasis.TOTAL), true)
        val fifteenEach = candidate(advertisement, 1, Price(15, PriceBasis.EACH), true)

        assertSame(fifteenEach,
            PlayerTradeAdvertisementSelection.choose(listOf(fourteenEach, fifteenEach), 0.0))
    }

    @Test fun fractionalTotalUnitQuotesAreNotRoundedDownBeforeRanking() {
        val advertisement = offer(Price(2_000, PriceBasis.TOTAL), 100)
        val nineEach = candidate(advertisement, 40, Price(9, PriceBasis.EACH), true)
        val nineAndAHalfEach = candidate(advertisement, 1, Price(950, PriceBasis.TOTAL), true)

        assertSame(nineAndAHalfEach,
            PlayerTradeAdvertisementSelection.choose(listOf(nineEach, nineAndAHalfEach), 0.0))
    }

    @Test fun equivalentEachAndTotalQuotesTieBeforeQuantityAndSampling() {
        val advertisement = offer(Price(2_000, PriceBasis.TOTAL), 100)
        val each = candidate(advertisement, 10, Price(10, PriceBasis.EACH), true)
        val total = candidate(advertisement, 10, Price(1_000, PriceBasis.TOTAL), true)

        assertSame(each, PlayerTradeAdvertisementSelection.choose(listOf(each, total), 0.0))
        assertSame(total, PlayerTradeAdvertisementSelection.choose(listOf(each, total), 1.0))
    }

    @Test fun rationalPriceCrossMultiplicationCannotOverflowLongValues() {
        val advertisement = offer(Price(Long.MAX_VALUE, PriceBasis.EACH), Int.MAX_VALUE)
        val lower = candidate(advertisement, 1, Price(Long.MAX_VALUE, PriceBasis.TOTAL), true)
        val higher = candidate(advertisement, 1, Price(Long.MAX_VALUE - 1, PriceBasis.EACH), true)

        assertSame(higher, PlayerTradeAdvertisementSelection.choose(listOf(lower, higher), 0.0))
    }

    @Test fun repeatedCandidatesForOneBuyerDoNotIncreaseItsTieWeight() {
        val advertisement = offer()
        val first = candidate(advertisement, 10)
        val second = candidate(advertisement, 10)

        assertSame(second, PlayerTradeAdvertisementSelection.choose(listOf(first, first.copy(), second), 0.5))
    }

    @Test fun duplicateBuyersKeepTheirBestQuantityCandidate() {
        val advertisement = offer()
        val buyer = mock(Bot::class.java)
        val small = candidate(advertisement, 1, buyer = buyer)
        val other = candidate(advertisement, 5)
        val large = candidate(advertisement, 10, buyer = buyer)

        assertSame(large, PlayerTradeAdvertisementSelection.choose(listOf(small, other, large), 0.0))
    }

    @Test fun duplicateBuyersKeepAnAcceptanceBeforeTheirCounteroffer() {
        val advertisement = offer()
        val buyer = mock(Bot::class.java)
        val counter = candidate(advertisement, 100, Price(99, PriceBasis.EACH), true, buyer)
        val accepted = candidate(advertisement, 1, buyer = buyer)
        val otherCounter = candidate(advertisement, 50, Price(99, PriceBasis.EACH), true)

        assertSame(accepted,
            PlayerTradeAdvertisementSelection.choose(listOf(counter, otherCounter, accepted), 1.0))
    }

    @Test fun replacingABuyersCandidatePreservesItsFirstAppearanceTieBucket() {
        val advertisement = offer()
        val buyer = mock(Bot::class.java)
        val small = candidate(advertisement, 1, buyer = buyer)
        val other = candidate(advertisement, 10)
        val larger = candidate(advertisement, 10, buyer = buyer)

        assertSame(larger, PlayerTradeAdvertisementSelection.choose(listOf(small, other, larger), 0.0))
        assertSame(other, PlayerTradeAdvertisementSelection.choose(listOf(small, other, larger), 1.0))
    }

    @Test fun equalAdvertisementCopiesCanParticipateInTheSameSelection() {
        val advertisement = offer()
        val first = candidate(advertisement, 1)
        val second = candidate(advertisement.copy(), 10)

        assertSame(second, PlayerTradeAdvertisementSelection.choose(listOf(first, second), 0.0))
    }

    @Test fun unrelatedItemsOrDifferentAdvertisedTermsCannotBeComparedAsOneOffer() {
        val advertisement = offer()
        val first = candidate(advertisement, 1)
        val different = listOf(
            advertisement.copy(itemIds = setOf(440)),
            advertisement.copy(quantity = 101),
            advertisement.copy(price = Price(101, PriceBasis.EACH))
        )
        for (other in different) {
            assertThrows(IllegalArgumentException::class.java) {
                PlayerTradeAdvertisementSelection.choose(listOf(first, candidate(other, 1)), 0.0)
            }
        }
    }

    @Test fun invalidSamplesAreRejectedEvenWhenThereAreNoCandidates() {
        val only = candidate(offer(), 1)
        for (sample in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01, 1.01)) {
            assertThrows(IllegalArgumentException::class.java) {
                PlayerTradeAdvertisementSelection.choose(listOf(only), sample)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PlayerTradeAdvertisementSelection.choose(emptyList(), sample)
            }
        }
    }

    @Test fun candidatesRequireAPositiveFundedPlan() {
        val valid = candidate(offer(), 1)
        val invalid = listOf(
            valid.plan.copy(quantity = 0),
            valid.plan.copy(quantity = -1),
            valid.plan.copy(quantity = 101),
            valid.plan.copy(cost = 0),
            valid.plan.copy(payment = emptyMap()),
            valid.plan.copy(payment = mapOf(453 to 0)),
            valid.plan.copy(payment = mapOf(453 to -1)),
            valid.plan.copy(paidValue = valid.plan.cost - 1)
        )
        for (plan in invalid) {
            assertThrows(IllegalArgumentException::class.java) { valid.copy(plan = plan) }
        }
    }

    @Test fun inspectionAndCoinAdvertisementsAreOutsidePricedSelection() {
        val valid = candidate(offer(), 1)
        val invalid = listOf(
            valid.offer.copy(itemIds = emptySet()),
            valid.offer.copy(itemIds = setOf(314, 453)),
            valid.offer.copy(itemIds = setOf(995)),
            valid.offer.copy(itemIds = setOf(-1)),
            valid.offer.copy(price = Price(100, PriceBasis.UNCLEAR)),
            offer(Price(100, PriceBasis.TOTAL), null)
        )
        for (advertisement in invalid) {
            assertThrows(IllegalArgumentException::class.java) { valid.copy(offer = advertisement) }
        }
    }

    @Test fun quotesNeedAClearBasisAndTotalQuotesNeedAnAdvertisedQuantity() {
        val valid = candidate(offer(), 1)

        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(quote = Quote(Price(100, PriceBasis.UNCLEAR), true))
        }
        assertThrows(IllegalArgumentException::class.java) {
            valid.copy(offer = offer(price = null, quantity = null),
                quote = Quote(Price(100, PriceBasis.TOTAL), true))
        }
    }
}
