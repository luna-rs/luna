package api.bot.action.trading

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TradeBasketTest {

    @Test fun fiftyCoinsBuysFiftyOfOneHundredFeathers() {
        assertEquals(mapOf(314 to 50), TradeBasket.match(mapOf(314 to 100), mapOf(314 to 100),
            mapOf(314 to 1L), mapOf(314 to 1L), 50))
    }

    @Test fun wantedQuantityCapsAnOtherwiseAffordablePurchase() {
        assertEquals(mapOf(314 to 30), TradeBasket.match(mapOf(314 to 100), mapOf(314 to 30),
            mapOf(314 to 1L), mapOf(314 to 5L), 500))
    }

    @Test fun incompatiblePricesAndMissingDemandCannotCreateAnExchange() {
        assertTrue(TradeBasket.match(mapOf(314 to 100), mapOf(314 to 100),
            mapOf(314 to 5L), mapOf(314 to 4L), 500).isEmpty())
        assertTrue(TradeBasket.match(mapOf(314 to 100), emptyMap(),
            mapOf(314 to 5L), mapOf(314 to 5L), 500).isEmpty())
    }

    @Test fun indivisibleItemsNeverExceedTheBudget() {
        assertTrue(TradeBasket.select(mapOf(4151 to 1), mapOf(4151 to 51L), 50).isEmpty())
        assertEquals(mapOf(4151 to 1), TradeBasket.select(mapOf(4151 to 2), mapOf(4151 to 30L), 50))
    }

    @Test fun emptyZeroPriceAndUnaffordableOffersCannotBeAccepted() {
        for (budget in listOf(-1L, 0L)) {
            assertTrue(TradeBasket.select(mapOf(314 to 100), mapOf(314 to 1L), budget).isEmpty())
        }
        assertTrue(TradeBasket.select(mapOf(314 to 100), mapOf(314 to 0L), 100).isEmpty())
        assertTrue(TradeBasket.select(emptyMap(), mapOf(314 to 1L), 100).isEmpty())
    }

    @Test fun recalculationUsesOriginalStockRatherThanMultiplyingThePreviousReduction() {
        val stock = mapOf(314 to 100)
        val prices = mapOf(314 to 1L)
        assertEquals(mapOf(314 to 50), TradeBasket.select(stock, prices, 50))
        assertEquals(mapOf(314 to 25), TradeBasket.select(stock, prices, 25))
        assertEquals(mapOf(314 to 75), TradeBasket.select(stock, prices, 75))
    }

    @Test fun mapInsertionOrderDoesNotChangeTheAgreedBasket() {
        val prices = mapOf(314 to 2L, 1511 to 3L)
        assertEquals(TradeBasket.select(linkedMapOf(314 to 10, 1511 to 10), prices, 25),
            TradeBasket.select(linkedMapOf(1511 to 10, 314 to 10), prices, 25))
    }

    @Test fun largeQuantitiesAndPricesCannotOverflowIntoAFreeOrNegativeTrade() {
        assertEquals(Long.MAX_VALUE, TradeBasket.value(mapOf(314 to Int.MAX_VALUE), mapOf(314 to Long.MAX_VALUE)))
        assertEquals(Long.MAX_VALUE, TradeBasket.value(mapOf(314 to 1, 1511 to 1),
            mapOf(314 to Long.MAX_VALUE, 1511 to 1L)))
        assertEquals(mapOf(314 to 1), TradeBasket.select(mapOf(314 to Int.MAX_VALUE),
            mapOf(314 to Long.MAX_VALUE), Long.MAX_VALUE))
    }

    @Test fun barterPaymentCanFundAPurchaseWithoutCoins() {
        assertEquals(mapOf(314 to 50) to mapOf(1511 to 1), TradeBasket.exchange(
            mapOf(314 to 100), mapOf(314 to 100), mapOf(314 to 1L), mapOf(314 to 1L),
            mapOf(1511 to 1), mapOf(1511 to 50L)))
    }

    @Test fun coinsAreSpentBeforeSurplusBarterStock() {
        assertEquals(mapOf(314 to 50) to mapOf(995 to 50), TradeBasket.exchange(
            mapOf(314 to 50), mapOf(314 to 50), mapOf(314 to 1L), mapOf(314 to 1L),
            mapOf(1511 to 2, 995 to 100), mapOf(1511 to 50L, 995 to 1L)))
    }

    @Test fun barterRoundingReducesSaleQuantitiesUntilWholeItemsMatch() {
        assertEquals(mapOf(314 to 3) to mapOf(1511 to 2), TradeBasket.exchange(
            mapOf(314 to 5), mapOf(314 to 5), mapOf(314 to 20L), mapOf(314 to 20L),
            mapOf(1511 to 3), mapOf(1511 to 30L)))
    }

    @Test fun indivisibleBarterMayOnlyOverpayWithinTheBuyersPriceLimit() {
        assertEquals(mapOf(314 to 1) to mapOf(1511 to 1), TradeBasket.exchange(
            mapOf(314 to 1), mapOf(314 to 1), mapOf(314 to 30L), mapOf(314 to 40L),
            mapOf(1511 to 1), mapOf(1511 to 40L)))
        assertNull(TradeBasket.exchange(mapOf(314 to 1), mapOf(314 to 1), mapOf(314 to 30L),
            mapOf(314 to 35L), mapOf(1511 to 1), mapOf(1511 to 40L)))
    }

    @Test fun sameItemCannotBeUsedToPayForItself() {
        assertNull(TradeBasket.exchange(mapOf(314 to 1), mapOf(314 to 1), mapOf(314 to 1L),
            mapOf(314 to 1L), mapOf(314 to 1), mapOf(314 to 1L)))
    }
}
