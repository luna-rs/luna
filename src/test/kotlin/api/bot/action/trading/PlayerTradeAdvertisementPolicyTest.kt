package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.Price
import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Profile
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Checks captured personality decisions and exact whole-coin advertisement quotes without a world or random draws.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementPolicyTest {

    private val ordinary = Profile()
    private val merchant = Profile(merchant = true)
    private val greedy = Profile(greedy = true)

    private fun offer(coins: Long? = 1_000L, basis: PriceBasis = PriceBasis.EACH,
                      quantity: Int? = 100, ids: Set<Int> = setOf(1779)): PlayerTradeAdvertisement =
        PlayerTradeAdvertisement("flax", ids, quantity, coins?.let { Price(it, basis) })

    @Test fun averageVisitorsHaveAFivePercentCuriosityChance() {
        assertEquals(0.05, PlayerTradeAdvertisementPolicy.curiosityChance(ordinary), 1e-12)
    }

    @Test fun curiosityRangesFromZeroToTwentyPercentForTheRelevantTraits() {
        val uninterested = Profile(confidence = 0.0, social = 0.0, merchantingInterest = 0.0)
        val keen = Profile(confidence = 1.0, social = 1.0, merchantingInterest = 1.0)

        assertEquals(0.0, PlayerTradeAdvertisementPolicy.curiosityChance(uninterested), 1e-12)
        assertEquals(0.20, PlayerTradeAdvertisementPolicy.curiosityChance(keen), 1e-12)
        assertTrue(PlayerTradeAdvertisementPolicy.curiosityChance(keen) >
            PlayerTradeAdvertisementPolicy.curiosityChance(ordinary))
        assertFalse(PlayerTradeAdvertisementPolicy.curious(uninterested, 0.0))
    }

    @Test fun curiosityUsesTheExplicitSampleAndAStrictProbabilityBoundary() {
        assertTrue(PlayerTradeAdvertisementPolicy.curious(ordinary, 0.0))
        assertTrue(PlayerTradeAdvertisementPolicy.curious(ordinary, 0.049))
        assertFalse(PlayerTradeAdvertisementPolicy.curious(ordinary, 0.05))
        assertFalse(PlayerTradeAdvertisementPolicy.curious(ordinary, 1.0))
        repeat(5) {
            assertTrue(PlayerTradeAdvertisementPolicy.curious(ordinary, 0.01))
            assertFalse(PlayerTradeAdvertisementPolicy.curious(ordinary, 0.99))
        }
    }

    @Test fun ordinaryBuyersStartAtFifteenPercentHaggling() {
        assertEquals(0.15, PlayerTradeAdvertisementPolicy.haggleChance(ordinary), 1e-12)
    }

    @Test fun eitherMerchantOrGreedyBuyersStartAtFiftyPercentWithoutStackingTheBonus() {
        assertEquals(0.50, PlayerTradeAdvertisementPolicy.haggleChance(merchant), 1e-12)
        assertEquals(0.50, PlayerTradeAdvertisementPolicy.haggleChance(greedy), 1e-12)
        assertEquals(0.50, PlayerTradeAdvertisementPolicy.haggleChance(Profile(merchant = true, greedy = true)), 1e-12)
    }

    @Test fun confidenceIntelligenceAndLowerKindnessEncourageHaggling() {
        val assertive = ordinary.copy(intelligence = 1.0, confidence = 1.0, kindness = 0.0)
        val accommodating = ordinary.copy(intelligence = 0.0, confidence = 0.0, kindness = 1.0)

        assertTrue(PlayerTradeAdvertisementPolicy.haggleChance(assertive) >
            PlayerTradeAdvertisementPolicy.haggleChance(ordinary))
        assertTrue(PlayerTradeAdvertisementPolicy.haggleChance(accommodating) <
            PlayerTradeAdvertisementPolicy.haggleChance(ordinary))
    }

    @Test fun finiteTraitsOutsideTheNormalRangeAreClampedForDecisions() {
        val outside = Profile(intelligence = 2.0, kindness = -2.0, confidence = 3.0,
            social = -3.0, merchantingInterest = 4.0)
        val bounded = Profile(intelligence = 1.0, kindness = 0.0, confidence = 1.0,
            social = 0.0, merchantingInterest = 1.0)

        assertEquals(PlayerTradeAdvertisementPolicy.curiosityChance(bounded),
            PlayerTradeAdvertisementPolicy.curiosityChance(outside), 1e-12)
        assertEquals(PlayerTradeAdvertisementPolicy.haggleChance(bounded),
            PlayerTradeAdvertisementPolicy.haggleChance(outside), 1e-12)
        assertEquals(PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, bounded, 0.0, 0.5),
            PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, outside, 0.0, 0.5))
    }

    @Test fun profilesRejectNonFinitePersonalityValues() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            val constructors = listOf<() -> Profile>(
                { Profile(intelligence = invalid) },
                { Profile(kindness = invalid) },
                { Profile(confidence = invalid) },
                { Profile(social = invalid) },
                { Profile(merchantingInterest = invalid) }
            )
            for (construct in constructors) {
                assertThrows(IllegalArgumentException::class.java) { construct() }
            }
        }
    }

    @Test fun rejectsNonFiniteAndOutOfRangeExplicitSamples() {
        for (invalid in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -0.01, 1.01)) {
            assertThrows(IllegalArgumentException::class.java) {
                PlayerTradeAdvertisementPolicy.curious(ordinary, invalid)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, ordinary, invalid, 0.5)
            }
            assertThrows(IllegalArgumentException::class.java) {
                PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, ordinary, 0.5, invalid)
            }
        }
    }

    @Test fun affordableAsksAreAcceptedWhenTheBuyerDoesNotHaggle() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = 25), 30, ordinary, 1.0, 1.0)!!

        assertEquals(Price(25, PriceBasis.EACH), quote.price)
        assertFalse(quote.counteroffer)
    }

    @Test fun hagglingStartsOnlyBelowTheChanceBoundary() {
        val accepted = PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, ordinary, 0.15, 1.0)!!
        val countered = PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, ordinary, 0.149, 1.0)!!

        assertFalse(accepted.counteroffer)
        assertEquals(1_000L, accepted.price.coins)
        assertTrue(countered.counteroffer)
        assertEquals(900L, countered.price.coins)
    }

    @Test fun ordinaryDiscountEndpointsAreFiveAndTenPercent() {
        val small = PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, ordinary, 0.0, 0.0)!!
        val large = PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, ordinary, 0.0, 1.0)!!

        assertEquals(950L, small.price.coins)
        assertEquals(900L, large.price.coins)
        assertTrue(small.counteroffer)
        assertTrue(large.counteroffer)
    }

    @Test fun ordinaryIntermediateUnitDiscountDoesNotRoundADecimalArtifactIntoAnotherCoin() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, ordinary, 0.0, 0.7)!!

        assertEquals(Price(915, PriceBasis.EACH), quote.price)
        assertTrue(quote.counteroffer)
    }

    @Test fun ordinaryIntermediateTotalDiscountPreservesExactLotTerms() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(basis = PriceBasis.TOTAL, quantity = 100),
            10, ordinary, 0.0, 0.7)!!

        assertEquals(Price(915, PriceBasis.TOTAL), quote.price)
        assertTrue(quote.counteroffer)
    }

    @Test fun ordinaryIntermediateDiscountRetainsWholeCoinPrecisionAtTheLongBoundary() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = Long.MAX_VALUE), Long.MAX_VALUE,
            ordinary, 0.0, 0.7)!!

        assertEquals(Price(8_439_385_413_722_119_864L, PriceBasis.EACH), quote.price)
        assertTrue(quote.counteroffer)
    }

    @Test fun merchantOrGreedyDiscountEndpointsAreTenAndTwentyFivePercent() {
        for (profile in listOf(merchant, greedy, Profile(merchant = true, greedy = true))) {
            val small = PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, profile, 0.0, 0.0)!!
            val large = PlayerTradeAdvertisementPolicy.quote(offer(), 1_000, profile, 0.0, 1.0)!!

            assertEquals(900L, small.price.coins, profile.toString())
            assertEquals(750L, large.price.coins, profile.toString())
            assertTrue(small.counteroffer)
            assertTrue(large.counteroffer)
        }
    }

    @Test fun roundsCounterMoneyUpToAvoidDiscountingBeyondTheChosenPercentage() {
        val ordinarySmall = PlayerTradeAdvertisementPolicy.quote(offer(coins = 25), 25, ordinary, 0.0, 0.0)!!
        val ordinaryLarge = PlayerTradeAdvertisementPolicy.quote(offer(coins = 25), 25, ordinary, 0.0, 1.0)!!
        val merchantLarge = PlayerTradeAdvertisementPolicy.quote(offer(coins = 25), 25, merchant, 0.0, 1.0)!!

        assertEquals(24L, ordinarySmall.price.coins)
        assertEquals(23L, ordinaryLarge.price.coins)
        assertEquals(19L, merchantLarge.price.coins)
    }

    @Test fun acceptsSmallAsksWhenNoLowerWholeCoinOfferFitsTheDiscount() {
        for (coins in 1L..3L) {
            for (profile in listOf(ordinary, merchant, greedy)) {
                val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = coins), coins, profile, 0.0, 1.0)!!

                assertEquals(coins, quote.price.coins, "$coins / $profile")
                assertFalse(quote.counteroffer, "$coins / $profile")
            }
        }
    }

    @Test fun anAskAboveTheLimitIsCounteredExactlyAtTheNormalLimitWithoutAnotherDiscount() {
        for (profile in listOf(ordinary, merchant, greedy)) {
            for (haggleRoll in listOf(0.0, 1.0)) {
                val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = 101), 100,
                    profile, haggleRoll, 1.0)!!

                assertEquals(Price(100, PriceBasis.EACH), quote.price, "$profile / $haggleRoll")
                assertTrue(quote.counteroffer)
            }
        }
    }

    @Test fun aOneCoinBuyingLimitStillProducesAPositiveAboveLimitCounteroffer() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = 2), 1, ordinary, 0.0, 1.0)!!

        assertEquals(Price(1, PriceBasis.EACH), quote.price)
        assertTrue(quote.counteroffer)
    }

    @Test fun unpricedKnownItemsUseTheNormalUnitBidWithoutHaggling() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = null), 25, merchant, 0.0, 1.0)!!

        assertEquals(Price(25, PriceBasis.EACH), quote.price)
        assertFalse(quote.counteroffer)
    }

    @Test fun inspectionOffersAreLeftToTheBroadBuyerPathEvenForMerchants() {
        val inspection = listOf(
            offer(ids = emptySet()),
            offer(ids = setOf(1779, 453)),
            offer(basis = PriceBasis.UNCLEAR),
            offer(basis = PriceBasis.TOTAL, quantity = null)
        )
        for (advertisement in inspection) {
            for (profile in listOf(ordinary, merchant)) {
                assertNull(PlayerTradeAdvertisementPolicy.quote(advertisement, 1_000, profile, 0.0, 0.0),
                    "$advertisement / $profile")
            }
        }
    }

    @Test fun nonPositiveValuationsCannotCreateQuotedPayments() {
        for (maximum in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNull(PlayerTradeAdvertisementPolicy.quote(offer(), maximum, ordinary, 0.0, 0.0))
            assertNull(PlayerTradeAdvertisementPolicy.quote(offer(coins = null), maximum, ordinary, 0.0, 0.0))
        }
    }

    @Test fun fractionalImpliedUnitPricesRemainExactAdvertisedTotals() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = 50, basis = PriceBasis.TOTAL,
            quantity = 100), 1, ordinary, 1.0, 0.0)!!

        assertEquals(Price(50, PriceBasis.TOTAL), quote.price)
        assertFalse(quote.counteroffer)
    }

    @Test fun affordableTotalHagglingDiscountsTheLotWithoutRoundingItsUnitPriceFirst() {
        val advertisement = offer(coins = 50, basis = PriceBasis.TOTAL, quantity = 100)
        val quote = PlayerTradeAdvertisementPolicy.quote(advertisement, 1, ordinary, 0.0, 0.0)!!

        assertEquals(Price(48, PriceBasis.TOTAL), quote.price)
        assertTrue(quote.counteroffer)
        assertEquals(100, advertisement.quantity)
        assertEquals(Price(50, PriceBasis.TOTAL), advertisement.price)
    }

    @Test fun aboveLimitTotalsUseTheExactFullLotCeiling() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = 151, basis = PriceBasis.TOTAL,
            quantity = 100), 1, merchant, 0.0, 1.0)!!

        assertEquals(Price(100, PriceBasis.TOTAL), quote.price)
        assertTrue(quote.counteroffer)
    }

    @Test fun totalCeilingsCanExceedIntegerCoinAmountsWithoutClipping() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = 6_442_450_942L, basis = PriceBasis.TOTAL,
            quantity = Int.MAX_VALUE), 3, ordinary, 1.0, 0.0)!!

        assertEquals(Price(6_442_450_941L, PriceBasis.TOTAL), quote.price)
        assertTrue(quote.counteroffer)
    }

    @Test fun multiplyingALargeUnitCeilingByTheLotQuantityCannotOverflowIntoAFalseCounteroffer() {
        val quote = PlayerTradeAdvertisementPolicy.quote(offer(coins = Long.MAX_VALUE, basis = PriceBasis.TOTAL,
            quantity = Int.MAX_VALUE), Long.MAX_VALUE, ordinary, 1.0, 0.0)!!

        assertEquals(Price(Long.MAX_VALUE, PriceBasis.TOTAL), quote.price)
        assertFalse(quote.counteroffer)
    }

    @Test fun largeCounterofferRoundingRetainsWholeCoinPrecision() {
        val advertisement = offer(coins = Long.MAX_VALUE)
        val ordinaryQuote = PlayerTradeAdvertisementPolicy.quote(advertisement, Long.MAX_VALUE,
            ordinary, 0.0, 0.0)!!
        val merchantQuote = PlayerTradeAdvertisementPolicy.quote(advertisement, Long.MAX_VALUE,
            merchant, 0.0, 1.0)!!

        assertEquals(8_762_203_435_012_037_017L, ordinaryQuote.price.coins)
        assertEquals(6_917_529_027_641_081_856L, merchantQuote.price.coins)
        assertTrue(ordinaryQuote.counteroffer)
        assertTrue(merchantQuote.counteroffer)
    }

    @Test fun repeatedQuotesWithTheSameCapturedSamplesAreDeterministic() {
        val advertisement = offer()
        val first = PlayerTradeAdvertisementPolicy.quote(advertisement, 1_000, ordinary, 0.0, 0.5)

        repeat(5) {
            assertEquals(first, PlayerTradeAdvertisementPolicy.quote(advertisement, 1_000, ordinary, 0.0, 0.5))
        }
    }

    @Test fun barterAllowancesStartAtTenTwentyFiveAndFiftyPercentAboveTheCashLimit() {
        assertEquals(110L, PlayerTradeAdvertisementPolicy.barterLimit(100, Profile(intelligence = 1.0)))
        assertEquals(125L, PlayerTradeAdvertisementPolicy.barterLimit(100, ordinary))
        assertEquals(150L, PlayerTradeAdvertisementPolicy.barterLimit(100, Profile(intelligence = 0.0)))
    }

    @Test fun higherIntelligenceNeverIncreasesTheBarterAllowance() {
        val limits = (0..10).map { intelligence ->
            PlayerTradeAdvertisementPolicy.barterLimit(1_000, Profile(intelligence = intelligence / 10.0))
        }

        assertEquals(1_500L, limits.first())
        assertEquals(1_100L, limits.last())
        assertTrue(limits.zipWithNext().all { (lessCareful, moreCareful) -> moreCareful <= lessCareful })
    }

    @Test fun barterAllowancesClampFiniteIntelligenceOutsideTheNormalRange() {
        assertEquals(PlayerTradeAdvertisementPolicy.barterLimit(100, Profile(intelligence = 0.0)),
            PlayerTradeAdvertisementPolicy.barterLimit(100, Profile(intelligence = -10.0)))
        assertEquals(PlayerTradeAdvertisementPolicy.barterLimit(100, Profile(intelligence = 1.0)),
            PlayerTradeAdvertisementPolicy.barterLimit(100, Profile(intelligence = 10.0)))
    }

    @Test fun barterAllowancesRoundDownToWholeCoins() {
        val expectations = listOf(
            Profile(intelligence = 1.0) to listOf(1L, 2L, 3L),
            ordinary to listOf(1L, 2L, 3L),
            Profile(intelligence = 0.0) to listOf(1L, 3L, 4L)
        )
        for ((profile, expected) in expectations) {
            for (index in expected.indices) {
                assertEquals(expected[index], PlayerTradeAdvertisementPolicy.barterLimit(index + 1L, profile),
                    "${index + 1} GP / $profile")
            }
        }
    }

    @Test fun nonPositiveCashLimitsCannotCreateABarterAllowance() {
        for (cashLimit in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertEquals(0L, PlayerTradeAdvertisementPolicy.barterLimit(cashLimit, ordinary))
        }
    }

    @Test fun overflowingBarterCeilingsSaturateAtTheLargestPositiveLong() {
        for (profile in listOf(ordinary, Profile(intelligence = 0.0), Profile(intelligence = 1.0))) {
            assertEquals(Long.MAX_VALUE, PlayerTradeAdvertisementPolicy.barterLimit(Long.MAX_VALUE, profile))
            assertEquals(Long.MAX_VALUE, PlayerTradeAdvertisementPolicy.barterLimit(Long.MAX_VALUE - 1, profile))
        }
    }

    @Test fun intermediateIntelligenceKeepsTheExactDecimalBarterAllowance() {
        assertEquals(1_178L, PlayerTradeAdvertisementPolicy.barterLimit(1_000, Profile(intelligence = 0.7)))
    }
}
