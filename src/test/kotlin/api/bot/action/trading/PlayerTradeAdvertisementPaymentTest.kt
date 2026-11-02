package api.bot.action.trading

import api.bot.action.trading.PlayerTradeAdvertisement.Price
import api.bot.action.trading.PlayerTradeAdvertisement.PriceBasis
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Profile
import api.bot.action.trading.PlayerTradeAdvertisementPolicy.Quote
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Checks exact advertisement payment values and maximal affordable quantities without choosing barter items.
 *
 * @author lare96
 */
class PlayerTradeAdvertisementPaymentTest {

    private fun offer(coins: Long = 50, basis: PriceBasis = PriceBasis.TOTAL,
                      stock: Int? = 100): PlayerTradeAdvertisement =
        PlayerTradeAdvertisement("feathers", setOf(314), stock, Price(coins, basis))

    private fun quote(offer: PlayerTradeAdvertisement): Quote = Quote(offer.price!!, false)

    @Test fun partialHalfCoinLotsRoundTheFinalPaymentUpToWholeCoins() {
        val advertisement = offer()
        val quote = quote(advertisement)

        assertEquals(13L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 25))
        assertEquals(25L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 50))
        assertEquals(50L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 100))
    }

    @Test fun aPartialLotsPaymentRoundsOnceRatherThanRoundingEveryUnit() {
        val advertisement = offer()
        val quote = quote(advertisement)

        assertEquals(1L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 1))
        assertEquals(1L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 2))
        assertEquals(2L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 3))
    }

    @Test fun repeatingFractionalUnitPricesRetainTheirExactLotRatio() {
        val advertisement = offer(coins = 2, stock = 3)
        val quote = quote(advertisement)

        assertEquals(1L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 1))
        assertEquals(2L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 2))
        assertEquals(2L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 3))
        assertEquals(1, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 3, 1))
        assertEquals(3, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 3, 2))
    }

    @Test fun twelveCoinsBuyTwentyFourOfTwentyFiveWantedHalfCoinItems() {
        val advertisement = offer()

        assertEquals(24, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement,
            quote(advertisement), 25, 12))
    }

    @Test fun integerUnitPricesMultiplyTheQuotedAmountByThePurchasedQuantity() {
        val advertisement = offer(coins = 25, basis = PriceBasis.EACH)

        assertEquals(625L, PlayerTradeAdvertisementPayment.cost(advertisement, quote(advertisement), 25))
        assertEquals(3, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement,
            quote(advertisement), 100, 99))
    }

    @Test fun theQuotedCounterofferReplacesTheOriginalUnitAskingPrice() {
        val advertisement = offer(coins = 25, basis = PriceBasis.EACH)
        val counter = Quote(Price(20, PriceBasis.EACH), true)

        assertEquals(500L, PlayerTradeAdvertisementPayment.cost(advertisement, counter, 25))
        assertEquals(2, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, counter, 10, 49))
    }

    @Test fun totalCounteroffersUseTheirOwnExactLotPrice() {
        val advertisement = offer()
        val counter = Quote(Price(45, PriceBasis.TOTAL), true)

        assertEquals(23L, PlayerTradeAdvertisementPayment.cost(advertisement, counter, 50))
        assertEquals(48, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, counter, 100, 22))
    }

    @Test fun noPriceAdvertisementsUseTheNormalUnitBidFromTheirQuote() {
        val advertisement = PlayerTradeAdvertisement("feathers", setOf(314), null, null)
        val normalBid = Quote(Price(17, PriceBasis.EACH), false)

        assertEquals(51L, PlayerTradeAdvertisementPayment.cost(advertisement, normalBid, 3))
        assertEquals(2, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, normalBid, 10, 50))
    }

    @Test fun affordabilityNeverExceedsTheAdvertisedStockOrTheBuyersDemand() {
        for (basis in listOf(PriceBasis.EACH, PriceBasis.TOTAL)) {
            val advertisement = offer(coins = 50, basis = basis)
            val quote = quote(advertisement)

            assertEquals(100, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 200, 50_000))
            assertEquals(25, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 25, 50_000))
            assertNull(PlayerTradeAdvertisementPayment.cost(advertisement, quote, 101))
        }
    }

    @Test fun unspecifiedStockAllowsAnyAffordableWantedWholeQuantity() {
        val advertisement = offer(coins = 2, basis = PriceBasis.EACH, stock = null)
        val quote = quote(advertisement)

        assertEquals(200L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 100))
        assertEquals(100, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 100, 500))
        assertEquals(99, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 100, 199))
    }

    @Test fun zeroQuantityCostsZeroForOtherwiseValidUnitAndTotalTerms() {
        for (basis in listOf(PriceBasis.EACH, PriceBasis.TOTAL)) {
            val advertisement = offer(basis = basis)

            assertEquals(0L, PlayerTradeAdvertisementPayment.cost(advertisement, quote(advertisement), 0))
        }
    }

    @Test fun negativeQuantitiesNeverBecomeValidPayments() {
        val advertisement = offer()
        for (quantity in listOf(-1, Int.MIN_VALUE)) {
            assertNull(PlayerTradeAdvertisementPayment.cost(advertisement, quote(advertisement), quantity))
        }
    }

    @Test fun nonPositiveDemandOrBudgetBuysNothing() {
        val advertisement = offer()
        val quote = quote(advertisement)
        for (wanted in listOf(0, -1, Int.MIN_VALUE)) {
            assertEquals(0, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, wanted, 100))
        }
        for (budget in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertEquals(0, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 100, budget))
        }
    }

    @Test fun unclearPriceTermsCannotBeValuedEvenAtZeroQuantity() {
        val advertisement = offer(basis = PriceBasis.UNCLEAR)
        val quote = quote(advertisement)

        assertNull(PlayerTradeAdvertisementPayment.cost(advertisement, quote, 0))
        assertNull(PlayerTradeAdvertisementPayment.cost(advertisement, quote, 1))
        assertEquals(0, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 100, 100))
    }

    @Test fun totalsWithoutAnAdvertisedLotSizeCannotBeValued() {
        val advertisement = offer(stock = null)
        val quote = quote(advertisement)

        assertNull(PlayerTradeAdvertisementPayment.cost(advertisement, quote, 0))
        assertNull(PlayerTradeAdvertisementPayment.cost(advertisement, quote, 1))
        assertEquals(0, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, 100, 100))
    }

    @Test fun overflowingUnitPaymentsAreRejectedRatherThanSaturatedOrWrapped() {
        val advertisement = offer(coins = Long.MAX_VALUE, basis = PriceBasis.EACH, stock = null)
        val quote = quote(advertisement)

        assertEquals(Long.MAX_VALUE, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 1))
        assertNull(PlayerTradeAdvertisementPayment.cost(advertisement, quote, 2))
        assertNull(PlayerTradeAdvertisementPayment.cost(advertisement, quote, Int.MAX_VALUE))
        assertEquals(1, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote,
            Int.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test fun unitAffordabilityWithAHugeBudgetStillCapsAtTheIntegerQuantityLimit() {
        val advertisement = offer(coins = 1, basis = PriceBasis.EACH, stock = null)

        assertEquals(Int.MAX_VALUE, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement,
            quote(advertisement), Int.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(Int.MAX_VALUE.toLong(), PlayerTradeAdvertisementPayment.cost(advertisement,
            quote(advertisement), Int.MAX_VALUE))
    }

    @Test fun largeTotalMultiplicationPreservesARepresentableFinalPayment() {
        val advertisement = offer(coins = Long.MAX_VALUE, stock = Int.MAX_VALUE)
        val quote = quote(advertisement)

        assertEquals(Long.MAX_VALUE, PlayerTradeAdvertisementPayment.cost(advertisement, quote, Int.MAX_VALUE))
        assertEquals(4_294_967_299L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 1))
    }

    @Test fun hugeBudgetTimesLotSizeCannotOverflowAffordability() {
        val advertisement = offer(coins = Long.MAX_VALUE, stock = Int.MAX_VALUE)
        val quote = quote(advertisement)

        assertEquals(Int.MAX_VALUE, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote,
            Int.MAX_VALUE, Long.MAX_VALUE))
        assertEquals(Int.MAX_VALUE - 1, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote,
            Int.MAX_VALUE, Long.MAX_VALUE - 1))
    }

    @Test fun evenASubCoinRatioRequiresOneCoinForANonEmptyPurchase() {
        val advertisement = offer(coins = 1, stock = Int.MAX_VALUE)
        val quote = quote(advertisement)

        assertEquals(1L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, 1))
        assertEquals(1L, PlayerTradeAdvertisementPayment.cost(advertisement, quote, Int.MAX_VALUE))
        assertEquals(0, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote, Int.MAX_VALUE, 0))
        assertEquals(Int.MAX_VALUE, PlayerTradeAdvertisementPayment.affordableQuantity(advertisement, quote,
            Int.MAX_VALUE, 1))
    }

    @Test fun smallTotalCostsAgreeWithWholeCoinCeilingsForEveryQuantity() {
        for (coins in 1L..7L) {
            for (stock in 1..7) {
                val advertisement = offer(coins = coins, stock = stock)
                val quote = quote(advertisement)
                for (quantity in 0..stock) {
                    val expected = (coins * quantity + stock - 1) / stock

                    assertEquals(expected, PlayerTradeAdvertisementPayment.cost(advertisement, quote, quantity),
                        "$quantity of $stock for $coins total")
                }
            }
        }
    }

    @Test fun smallGridAffordabilityIsTheLargestQuantityWhoseRoundedCostFitsTheBudget() {
        for (basis in listOf(PriceBasis.EACH, PriceBasis.TOTAL)) {
            for (coins in 1L..7L) {
                for (stock in 1..7) {
                    val advertisement = offer(coins = coins, basis = basis, stock = stock)
                    val quote = quote(advertisement)
                    for (wanted in 0..stock + 2) {
                        for (budget in 0L..12L) {
                            val expected = (0..minOf(stock, wanted)).last { quantity ->
                                val cost = if (basis == PriceBasis.EACH) coins * quantity
                                else (coins * quantity + stock - 1) / stock
                                cost <= budget
                            }

                            assertEquals(expected, PlayerTradeAdvertisementPayment.affordableQuantity(
                                advertisement, quote, wanted, budget),
                                "$basis: wanted $wanted of $stock at $coins with $budget GP")
                        }
                    }
                }
            }
        }
    }

    @Test fun aFullyFundedPurchaseUsesOnlyTheExactCoinPayment() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(995 to 150, 453 to 1), mapOf(995 to 1L, 453 to 110L))!!

        assertEquals(1, plan.quantity)
        assertEquals(mapOf(995 to 100), plan.payment)
        assertEquals(100L, plan.cost)
        assertEquals(100L, plan.paidValue)
    }

    @Test fun itemsSupplementCoinsAndUnusedCoinsAreTrimmedBackOut() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(995 to 90, 453 to 1), mapOf(995 to 1L, 453 to 20L))!!

        assertEquals(mapOf(995 to 80, 453 to 1), plan.payment)
        assertEquals(100L, plan.cost)
        assertEquals(100L, plan.paidValue)
    }

    @Test fun alternateItemOrdersCanFindTheApprovedThirtyPlusEightyPayment() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(453 to 2, 440 to 1), mapOf(453 to 30L, 440 to 80L))!!

        assertEquals(1, plan.quantity)
        assertEquals(mapOf(453 to 1, 440 to 1), plan.payment)
        assertEquals(100L, plan.cost)
        assertEquals(110L, plan.paidValue)
    }

    @Test fun anIndivisibleBackupItemCanOverpayWithinTheNormalAllowance() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(453 to 1), mapOf(453 to 110L))!!

        assertEquals(mapOf(453 to 1), plan.payment)
        assertEquals(100L, plan.cost)
        assertEquals(110L, plan.paidValue)
    }

    @Test fun aBackupItemAboveTheAverageAllowanceCannotBeSpent() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)

        assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(453 to 1), mapOf(453 to 130L)))
    }

    @Test fun personalityBoundsIndivisibleBackupOverpayment() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val careless = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100,
            Profile(intelligence = 0.0), 1, mapOf(453 to 1), mapOf(453 to 140L))!!

        assertEquals(140L, careless.paidValue)
        assertEquals(mapOf(453 to 1), careless.payment)
        assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100,
            Profile(intelligence = 1.0), 1, mapOf(453 to 1), mapOf(453 to 120L)))
    }

    @Test fun theLowestValueSimpleFeasiblePaymentWins() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(453 to 1, 440 to 2), mapOf(453 to 110L, 440 to 60L))!!

        assertEquals(mapOf(453 to 1), plan.payment)
        assertEquals(110L, plan.paidValue)
    }

    @Test fun equallyValuedSimplePaymentsPreferUsingMoreCoins() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(995 to 90, 453 to 1, 440 to 1), mapOf(453 to 20L, 440 to 80L))!!

        assertEquals(mapOf(995 to 80, 453 to 1), plan.payment)
        assertEquals(100L, plan.paidValue)
    }

    @Test fun thePurchasedCommodityCannotBeUsedToPayForItself() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)

        assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(314 to 10), mapOf(314 to 100L)))
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(314 to 10, 995 to 100), mapOf(314 to 100L))!!
        assertEquals(mapOf(995 to 100), plan.payment)
    }

    @Test fun coinsAndUnknownOrGroupedCommoditiesCannotBePurchasedByThisComposer() {
        for (ids in listOf(setOf(995), emptySet(), setOf(314, 453))) {
            val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1).copy(itemIds = ids)

            assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
                1, mapOf(995 to 100), emptyMap()))
        }
    }

    @Test fun itemsWithoutPositiveGuideValuesOrQuantitiesCannotFundAPurchase() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val invalidPayments = listOf(
            mapOf(453 to 1) to emptyMap<Int, Long>(),
            mapOf(453 to 1) to mapOf(453 to 0L),
            mapOf(453 to 1) to mapOf(453 to -100L),
            mapOf(453 to 0) to mapOf(453 to 100L),
            mapOf(453 to -1) to mapOf(453 to 100L)
        )
        for ((stock, prices) in invalidPayments) {
            assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
                1, stock, prices), "$stock / $prices")
        }
    }

    @Test fun suppliedCoinGuideValuesCannotInflateTheWallet() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)

        assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(995 to 1), mapOf(995 to Long.MAX_VALUE)))
    }

    @Test fun coinsRemainWorthOneEvenWhenTheirGuidePriceIsMissingOrInvalid() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        for (prices in listOf(emptyMap(), mapOf(995 to 0L), mapOf(995 to -1L), mapOf(995 to Long.MAX_VALUE))) {
            val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
                1, mapOf(995 to 100), prices)!!

            assertEquals(mapOf(995 to 100), plan.payment, prices.toString())
            assertEquals(100L, plan.paidValue, prices.toString())
        }
    }

    @Test fun compositionCapsThePurchaseByStockDemandAndActualFunds() {
        val advertisement = offer(coins = 5, basis = PriceBasis.EACH, stock = 10)
        val stockCapped = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 5, Profile(),
            20, mapOf(995 to 100), emptyMap())!!
        val demandCapped = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 5, Profile(),
            3, mapOf(995 to 100), emptyMap())!!
        val budgetCapped = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 5, Profile(),
            10, mapOf(995 to 12), emptyMap())!!

        assertEquals(10, stockCapped.quantity)
        assertEquals(mapOf(995 to 50), stockCapped.payment)
        assertEquals(3, demandCapped.quantity)
        assertEquals(mapOf(995 to 15), demandCapped.payment)
        assertEquals(2, budgetCapped.quantity)
        assertEquals(mapOf(995 to 10), budgetCapped.payment)
    }

    @Test fun actualBackupStockRemainsAHardBudgetLimit() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)

        assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100,
            Profile(intelligence = 0.0), 1, mapOf(453 to 1), mapOf(453 to 60L)))
    }

    @Test fun partialTotalCompositionKeepsTheExactRatioAndRoundsTheFinalCostUp() {
        val advertisement = offer()
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 1, Profile(),
            25, mapOf(995 to 6, 453 to 1), mapOf(453 to 10L))!!

        assertEquals(25, plan.quantity)
        assertEquals(13L, plan.cost)
        assertEquals(mapOf(995 to 3, 453 to 1), plan.payment)
        assertEquals(13L, plan.paidValue)
    }

    @Test fun anImpossibleFullOrderStillFallsBackToTheSmallerCoinPurchase() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 2)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            2, mapOf(995 to 100, 453 to 1), mapOf(453 to 260L))!!

        assertEquals(1, plan.quantity)
        assertEquals(mapOf(995 to 100), plan.payment)
        assertEquals(100L, plan.cost)
        assertEquals(100L, plan.paidValue)
    }

    @Test fun hugeUnusableItemValueDoesNotHideTheCoinOnlyFallback() {
        val advertisement = offer(coins = 1, basis = PriceBasis.EACH, stock = Int.MAX_VALUE)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 1, Profile(),
            Int.MAX_VALUE, mapOf(995 to 10, 453 to 1), mapOf(453 to Long.MAX_VALUE))!!

        assertEquals(10, plan.quantity)
        assertEquals(mapOf(995 to 10), plan.payment)
        assertEquals(10L, plan.paidValue)
    }

    @Test fun theItemAllowanceCannotAuthorizeCashAboveTheNormalValuation() {
        val advertisement = offer(coins = 110, basis = PriceBasis.EACH, stock = 1)

        assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100,
            Profile(intelligence = 0.0), 1, mapOf(995 to 110), emptyMap()))
    }

    @Test fun hugeStockValuationsCannotOverflowARepresentableOneItemPayment() {
        val advertisement = offer(coins = Long.MAX_VALUE, basis = PriceBasis.EACH, stock = Int.MAX_VALUE)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), Long.MAX_VALUE,
            Profile(), Int.MAX_VALUE, mapOf(453 to Int.MAX_VALUE), mapOf(453 to Long.MAX_VALUE))!!

        assertEquals(1, plan.quantity)
        assertEquals(mapOf(453 to 1), plan.payment)
        assertEquals(Long.MAX_VALUE, plan.cost)
        assertEquals(Long.MAX_VALUE, plan.paidValue)
    }

    @Test fun theSpendingLimitCapsTheCoinPurchaseDespiteALargerWallet() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 2)
        for (wallet in listOf(150, 250)) {
            val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
                2, mapOf(995 to wallet), emptyMap(), spendingLimit = 100)!!

            assertEquals(1, plan.quantity, "$wallet coins")
            assertEquals(mapOf(995 to 100), plan.payment)
            assertEquals(100L, plan.cost)
            assertEquals(100L, plan.paidValue)
        }
    }

    @Test fun anAllowedItemPremiumCannotExceedTheActualSpendingLimit() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)

        assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(453 to 1), mapOf(453 to 110L), spendingLimit = 100))
    }

    @Test fun trimmedMixedPaymentCanFitTheSpendingLimitExactly() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
            1, mapOf(995 to 90, 453 to 1), mapOf(453 to 20L), spendingLimit = 100)!!

        assertEquals(1, plan.quantity)
        assertEquals(mapOf(995 to 80, 453 to 1), plan.payment)
        assertEquals(100L, plan.cost)
        assertEquals(100L, plan.paidValue)
    }

    @Test fun aCarelessBarterAllowanceCannotOverrideASmallerSpendingLimit() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)

        assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100,
            Profile(intelligence = 0.0), 1, mapOf(453 to 1), mapOf(453 to 140L), spendingLimit = 120))
    }

    @Test fun nonPositiveSpendingLimitsCannotCreateAPaymentPlan() {
        val advertisement = offer(coins = 100, basis = PriceBasis.EACH, stock = 1)
        for (limit in listOf(0L, -1L, Long.MIN_VALUE)) {
            assertNull(PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 100, Profile(),
                1, mapOf(995 to 100), emptyMap(), spendingLimit = limit))
        }
    }

    @Test fun aSpendingLimitPreservesExactPartialLotAffordability() {
        val advertisement = offer()
        val plan = PlayerTradeAdvertisementPayment.plan(advertisement, quote(advertisement), 1, Profile(),
            25, mapOf(995 to 90), emptyMap(), spendingLimit = 12)!!

        assertEquals(24, plan.quantity)
        assertEquals(mapOf(995 to 12), plan.payment)
        assertEquals(12L, plan.cost)
        assertEquals(12L, plan.paidValue)
    }
}
