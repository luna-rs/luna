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
 * Verifies liquidity classification remains independent of the bounded merchant resale price range.
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
                        assertEquals(1.0, liquidity)
                        val bid = BotMerchantValuation.resaleBid(1_000, intelligence, greedy, sample)
                        assertTrue(bid in 750L..900L, "Unexpected liquid bid $bid")
                    }
                }
            }
        }
    }

    @Test
    fun cheapSuppliesKeepTheDiscountWithinOneQuarterAtCoinResolution() {
        assertEquals(4L, BotMerchantValuation.resaleBid(5, 1.0, true, 0.0))
    }

    @Test
    fun sparseNonSupplyHistoryDoesNotUndercutTheResaleFloor() {
        val confidence = listOf(-1L, 0, 25, 284, 500, 1_000, Long.MAX_VALUE).map { samples ->
            BotMerchantValuation.liquidity(samples, false)
        }
        assertEquals(confidence.sorted(), confidence)
        assertEquals(0.0, confidence.first())
        assertEquals(1.0, confidence.last())
        assertEquals(7_500L, BotMerchantValuation.resaleBid(10_000, 1.0, true, 0.0))
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
            assertTrue(quote.buy(314, 100) in 75L..90L)
            assertTrue(quote.buy(1123, 10_000) in 7_500L..9_000L)
            prices[1123] = ItemPriceData(1123, 10_000.0, 10_000.0, 1_000, 0)
            assertTrue(BotMerchantValuation.liquidMarket(1123))
            val established = BotMerchantValuation.Quotes(bot, 1.0, true, 0, 1, 1, emptyList())
            assertTrue(established.buy(1123, 10_000) in 7_500L..9_000L)
        } finally {
            `when`(world.economy).thenReturn(originalEconomy)
        }
    }
}
