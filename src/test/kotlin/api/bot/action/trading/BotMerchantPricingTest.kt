package api.bot.action.trading

import api.bot.script.InventoryProductionFixtures
import io.luna.game.model.item.Item
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * Verifies merchant purchase quotes protect wanted stock and cosmetic equipment.
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
    fun cosmeticsReceiveGuidePriceEvenWithoutAPersonalWantedEntry() {
        assertFalse(bot.preferences.hasWantedItem(1038))
        assertEquals(1_000L, snapshot().buy(1038, 1_000))
    }

    @Test
    fun protectedBidsHoldAcrossJudgmentMoodAndRandomExtremes() {
        for (intelligence in listOf(0.0, 0.5, 1.0)) {
            for (greedy in listOf(false, true)) {
                for (sample in listOf(0.0, 0.5, 1.0)) {
                    assertTrue(BotMerchantValuation.bid(1_000, intelligence, greedy, false,
                        false, false, true, sample) >= 1_000)
                    assertTrue(BotMerchantValuation.bid(1_000, intelligence, greedy, true,
                        false, false, false, sample) >= 1_000)
                }
            }
        }
        assertTrue(BotMerchantValuation.bid(1_000, 0.0, true, true,
            true, true, false, 1.0) > 1_000)
    }

    @Test
    fun unrelatedStockRetainsTheMerchantDiscountAndOneCoinMinimum() {
        assertTrue(snapshot().buy(1511, 101) in 10L..40L)
        assertEquals(1L, snapshot().buy(1511, 1))
    }

    @Test
    fun mixedOffersAddEachItemsOwnQuoteWithoutDiscountingWantedOrCosmeticItems() {
        val quote = snapshot()
        val prices = mapOf(314 to quote.buy(314, 100, useful = true),
            1038 to quote.buy(1038, 1_000), 1511 to quote.buy(1511, 101))
        assertEquals(1_200L + 3 * prices.getValue(1511),
            TradeBasket.value(mapOf(314 to 2, 1038 to 1, 1511 to 3), prices))
    }

    @Test
    fun protectedCosmeticQuotesStayWithinTheAvailableBudget() {
        val affordable = affordableCosmetics(2_500).single()
        assertEquals(2, affordable.item.amount)
        assertEquals(1_000L, affordable.price)
        assertTrue(affordableCosmetics(999).isEmpty())
    }
}
