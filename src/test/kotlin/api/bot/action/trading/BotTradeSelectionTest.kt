package api.bot.action.trading

import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Verifies merchant sale batches retain minimum value and use observed trading history for priority.
 *
 * @author lare96
 */
class BotTradeSelectionTest {

    private val bot = mock(Bot::class.java)

    private fun quote(id: Int, amount: Int, price: Long): BotTradeItem =
        BotTradeItem(Item(id, amount), price, BotTradePurpose.MERCHANT_SELL, bot)

    @Test fun onlyStacksWorthStrictlyMoreThanOneThousandCoinsQualify() {
        val candidates = listOf(quote(7700, 1, 1_000), quote(7701, 1, 1_001),
            quote(7702, 1_000, 1), quote(7703, 1_001, 1))

        assertEquals(listOf(7701, 7703), BotTradeSelection.merchantSales(candidates) { 0 }.map { it.item.id })
    }

    @Test fun sevenMostValuableWholeStacksAreSelectedInsteadOfTheHighestUnitPrices() {
        val valuableStack = quote(7700, 1_000, 2)
        val candidates = listOf(valuableStack, quote(7701, 1, 1_900)) +
            (7702..7707).map { quote(it, 1, 1_800L - (it - 7702) * 100) }

        val selected = BotTradeSelection.merchantSales(candidates) { 0 }

        assertEquals((7700..7706).toList(), selected.map { it.item.id })
        assertSame(valuableStack, selected.first())
        assertEquals(1_000, selected.first().item.amount)
    }

    @Test fun recordedDemandCanDisplaceMoreExpensiveStockFromTheBatch() {
        val candidates = (7700..7706).map { quote(it, 1, 2_000L + it) } + quote(7800, 1, 1_001)
        val selected = BotTradeSelection.merchantSales(candidates) { if (it == 7800) Long.MAX_VALUE else 0 }

        assertEquals(7, selected.size)
        assertEquals(7800, selected.first().item.id)
        assertEquals((7701..7706).toSet() + 7800, selected.map { it.item.id }.toSet())
    }

    @Test fun selectedStacksUseSamplePriorityThenTotalStackValue() {
        val candidates = listOf(quote(7700, 1, 5_000), quote(7701, 1, 2_000), quote(7702, 1, 6_000))
        val samples = mapOf(7700 to 2L, 7701 to 20L, 7702 to 20L)

        assertEquals(listOf(7702, 7701, 7700),
            BotTradeSelection.merchantSales(candidates) { samples.getValue(it) }.map { it.item.id })
    }

    @Test fun equalValuesAtTheBatchLimitFavorMoreTradingSamples() {
        val candidates = (7700..7707).map { quote(it, 1, 2_000) }

        assertEquals((7707 downTo 7701).toList(),
            BotTradeSelection.merchantSales(candidates) { it.toLong() }.map { it.item.id })
    }

    @Test fun itemsWithoutSamplesHaveStableValueAndIdentifierOrdering() {
        val candidates = listOf(quote(7701, 1, 3_000), quote(7700, 1, 3_000), quote(7702, 1, 4_000))

        assertEquals(listOf(7702, 7700, 7701),
            BotTradeSelection.merchantSales(candidates) { 0 }.map { it.item.id })
    }

    @Test fun stackValuesAboveTheIntegerLimitRemainDistinctWhenChoosingSeven() {
        val candidates = (7700..7707).map { quote(it, 2, 1_500_000_000L + (it - 7700) * 100_000_000L) }

        assertEquals((7707 downTo 7701).toList(),
            BotTradeSelection.merchantSales(candidates) { 0 }.map { it.item.id })
    }

    @Test fun overflowingStackValuesCannotBecomeCheapOrNegative() {
        val candidates = listOf(quote(7700, Int.MAX_VALUE, Long.MAX_VALUE), quote(7701, 2, 3_000_000_000L))

        assertEquals(listOf(7700, 7701),
            BotTradeSelection.merchantSales(candidates) { 0 }.map { it.item.id })
    }

    @Test fun aDepletedBatchCanBeReplacedByOtherEligibleStock() {
        val stock = (7700..7708).map { quote(it, 1, 10_000L - (it - 7700) * 1_000) }
        val firstBatch = BotTradeSelection.merchantSales(stock) { 0 }
        val secondBatch = BotTradeSelection.merchantSales(stock.filterNot { it in firstBatch }) { 0 }

        assertEquals((7700..7706).toList(), firstBatch.map { it.item.id })
        assertEquals(listOf(7707, 7708), secondBatch.map { it.item.id })
        assertTrue(BotTradeSelection.merchantSales(emptyList()) { 0 }.isEmpty())
    }

    @Test fun partialSalesDoNotKeepResidualStacksBelowTheMinimumValue() {
        val remainder = listOf(quote(7700, 1_000, 1), quote(7701, 1_001, 1), quote(7702, 0, 5_000))

        assertEquals(listOf(7701), BotTradeSelection.merchantSales(remainder) { 0 }.map { it.item.id })
    }
}
