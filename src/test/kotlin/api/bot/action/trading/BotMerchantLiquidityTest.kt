package api.bot.action.trading

import api.bot.script.InventoryProductionFixtures
import api.predef.world
import io.luna.game.model.item.economy.ItemPriceData
import io.luna.game.model.item.economy.WorldEconomy
import io.luna.game.model.def.WantedItemDefinition
import io.luna.util.parser.impl.WantedItemDefinitionFileParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Verifies merchant resale discounts follow actual liquidity rather than a blanket liquidation bid.
 *
 * @author lare96
 */
class BotMerchantLiquidityTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            if (WantedItemDefinition.DEFAULT.size == 0) WantedItemDefinitionFileParser().run()
        }
    }

    @Test
    fun commonSuppliesAndEstablishedMarketsStayCloseToGuideAcrossPersonalityExtremes() {
        for (supply in listOf(false, true)) {
            val liquidity = BotMerchantValuation.liquidity(if (supply) 0 else 1_000, supply)
            for (intelligence in listOf(0.0, 0.5, 1.0)) {
                for (greedy in listOf(false, true)) {
                    for (sample in listOf(0.0, 0.5, 1.0)) {
                        val bid = BotMerchantValuation.bid(1_000, intelligence, greedy, false,
                            false, false, false, sample, liquidity)
                        assertTrue(bid in 750L..951L, "Unexpected liquid bid $bid")
                    }
                }
            }
        }
    }

    @Test
    fun cheapSuppliesKeepTheDiscountWithinOneQuarterAtCoinResolution() {
        assertEquals(4L, BotMerchantValuation.bid(5, 1.0, true, false,
            false, false, false, 0.0, 1.0))
    }

    @Test
    fun sparseNonSupplyHistoryRetainsResaleRiskWhileProtectionGrowsMonotonically() {
        val bids = listOf(-1L, 0, 25, 284, 500, 1_000, Long.MAX_VALUE).map { samples ->
            BotMerchantValuation.bid(10_000, 1.0, true, false, false, false, false,
                0.0, BotMerchantValuation.liquidity(samples, false))
        }
        assertEquals(bids.sorted(), bids)
        assertEquals(bids[0], bids[1])
        assertTrue(bids[2] < 2_000)
        assertEquals(bids[bids.lastIndex - 1], bids.last())
    }

    @Test
    fun actualQuotesProtectSuppliesWithoutTurningSeedOnlyEquipmentIntoAnEstablishedMarket() {
        val bot = InventoryProductionFixtures.bot()
        val originalEconomy = world.economy
        val economy = mock(WorldEconomy::class.java)
        val prices = mutableMapOf(1123 to ItemPriceData(1123, 10_000.0, 10_000.0, 0, 0))
        `when`(economy.prices).thenReturn(prices)
        `when`(world.economy).thenReturn(economy)
        try {
            assertTrue(BotMerchantValuation.liquidMarket(314))
            assertFalse(BotMerchantValuation.liquidMarket(1123))
            val quote = BotMerchantValuation.Quotes(bot, 1.0, true, 0, 1, 1, emptyList())
            assertTrue(quote.buy(314, 100) in 75L..100L)
            assertTrue(quote.buy(1123, 10_000) < 5_000)
            prices[1123] = ItemPriceData(1123, 10_000.0, 10_000.0, 1_000, 0)
            assertTrue(BotMerchantValuation.liquidMarket(1123))
            val established = BotMerchantValuation.Quotes(bot, 1.0, true, 0, 1, 1, emptyList())
            assertTrue(established.buy(1123, 10_000) >= 7_500)
        } finally {
            `when`(world.economy).thenReturn(originalEconomy)
        }
    }
}
