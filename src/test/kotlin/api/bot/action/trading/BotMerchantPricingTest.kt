package api.bot.action.trading

import api.bot.script.InventoryProductionFixtures
import io.luna.game.model.item.Item
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Verifies merchant resale prices share one bounded margin while personal purchases retain separate valuations.
 *
 * @author lare96
 */
class BotMerchantPricingTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun fixtures() = InventoryProductionFixtures.initializeCombat()
    }

    private val bot = InventoryProductionFixtures.bot()

    private fun snapshot() = BotMerchantValuation.Quotes(bot, 1.0, false, 0, 1, 1, emptyList())

    private fun affordableCosmetics(budget: Int): List<BotTradeItem> {
        val price = snapshot().buy(1038, 1_000)
        return BotTradeManager.affordablePurchases(
            listOf(BotTradeItem(Item(1038, Int.MAX_VALUE), price, BotTradePurpose.MERCHANT_BUY, bot)),
            listOf(BotTradeItem(Item(995, budget), 1, BotTradePurpose.MERCHANT_BUY, bot)))
    }

    @Test
    fun exactStrategyPreservesBuyingAndSellingPrices() {
        assertEquals(1_000L, BotTradePrice.EXACT.applyBuy(1_000))
        assertEquals(1_000L, BotTradePrice.EXACT.applySell(1_000))
    }

    @Test
    fun wantedStockReceivesAtLeastItsReferenceValue() {
        assertEquals(100L, snapshot().buy(314, 100, useful = true))
    }

    @Test
    fun cosmeticResaleStockReceivesTheSameMarginAsOtherTradeableStock() {
        assertFalse(bot.preferences.hasWantedItem(1038))
        assertTrue(snapshot().buy(1038, 1_000) in 750L..900L)
    }

    @Test
    fun resaleBidsStayWithinTheRangeAcrossJudgmentMoodAndRandomExtremes() {
        for (intelligence in listOf(0.0, 0.5, 1.0)) {
            for (greedy in listOf(false, true)) {
                for (sample in listOf(0.0, 0.5, 1.0)) {
                    assertTrue(BotMerchantValuation.resaleBid(1_000, intelligence, greedy, sample) in 750L..900L)
                }
            }
        }
        assertEquals(750L, BotMerchantValuation.resaleBid(1_000, 1.0, true, 0.0))
        assertEquals(900L, BotMerchantValuation.resaleBid(1_000, 0.0, false, 1.0))
    }

    @Test
    fun unrelatedStockRetainsTheMerchantDiscountAndOneCoinMinimum() {
        assertTrue(snapshot().buy(1123, 101) in 76L..90L)
        assertEquals(1L, snapshot().buy(1123, 1))
    }

    @Test
    fun mixedOffersAddEachItemsOwnQuoteWithSeparatePersonalAndResaleValuation() {
        val quote = snapshot()
        val prices = mapOf(314 to quote.buy(314, 100, useful = true),
            1038 to quote.buy(1038, 1_000), 1511 to quote.buy(1511, 101))
        assertEquals(200L + prices.getValue(1038) + 3 * prices.getValue(1511),
            TradeBasket.value(mapOf(314 to 2, 1038 to 1, 1511 to 3), prices))
    }

    @Test
    fun cosmeticResaleQuotesStayWithinTheAvailableBudget() {
        val affordable = affordableCosmetics(2_500).single()
        assertTrue(affordable.item.amount in 2..3)
        assertTrue(affordable.price in 750L..900L)
        assertTrue(affordable.totalPrice() <= 2_500)
        assertTrue(affordableCosmetics(749).isEmpty())
    }

    @Test
    fun repeatedItemQuotesUseOneCapturedResaleBid() {
        val quote = snapshot()
        val first = quote.buy(1123, 10_000)
        repeat(1_000) { assertEquals(first, quote.buy(1123, 99_999)) }
        assertEquals(10_000L, quote.buy(1123, 10_000, useful = true))
    }

    @Test
    fun maximumRepresentableReferenceKeepsItsMarginWithoutOverflow() {
        val minimum = java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.valueOf(75))
            .add(java.math.BigInteger.valueOf(99)).divide(java.math.BigInteger.valueOf(100)).longValueExact()
        val maximum = java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.valueOf(90))
            .divide(java.math.BigInteger.valueOf(100)).longValueExact()
        for (sample in listOf(0.0, 0.5, 1.0)) {
            assertTrue(BotMerchantValuation.resaleBid(Long.MAX_VALUE, 0.5, false, sample) in minimum..maximum)
        }
    }
}
