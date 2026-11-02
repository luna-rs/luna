package api.bot.action.trading

import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

/**
 * Verifies merchant sale batches retain valuable stock and use observed trading history for priority.
 *
 * @author lare96
 */
class BotTradeSelectionTest {

    private val bot = mock(Bot::class.java)

    private fun quote(id: Int, amount: Int, price: Long): BotTradeItem =
        BotTradeItem(Item(id, amount), price, BotTradePurpose.MERCHANT_SELL, bot)

    @Test fun onlyStacksWorthStrictlyMoreThanOneThousandCoinsQualify() {
        val candidates = listOf(quote(100, 1, 1_000), quote(101, 1, 1_001),
            quote(102, 1_000, 1), quote(103, 1_001, 1))

        assertEquals(listOf(101, 103), BotTradeSelection.merchantSales(candidates) { 0 }.map { it.item.id })
    }

    @Test fun sevenMostValuableWholeStacksAreSelectedInsteadOfTheHighestUnitPrices() {
        val valuableStack = quote(100, 1_000, 2)
        val candidates = listOf(valuableStack, quote(101, 1, 1_900)) +
            (102..107).map { quote(it, 1, 1_800L - (it - 102) * 100) }

        val selected = BotTradeSelection.merchantSales(candidates) { 0 }

        assertEquals((100..106).toList(), selected.map { it.item.id })
        assertSame(valuableStack, selected.first())
        assertEquals(1_000, selected.first().item.amount)
    }

    @Test fun popularityCannotDisplaceOneOfTheSevenMostValuableStacks() {
        val candidates = (100..106).map { quote(it, 1, 2_000L + it) } + quote(200, 1, 1_001)
        val selected = BotTradeSelection.merchantSales(candidates) { if (it == 200) Long.MAX_VALUE else 0 }

        assertEquals(7, selected.size)
        assertEquals((100..106).toSet(), selected.map { it.item.id }.toSet())
    }

    @Test fun selectedStacksUseSamplePriorityThenTotalStackValue() {
        val candidates = listOf(quote(100, 1, 5_000), quote(101, 1, 2_000), quote(102, 1, 6_000))
        val samples = mapOf(100 to 2L, 101 to 20L, 102 to 20L)

        assertEquals(listOf(102, 101, 100),
            BotTradeSelection.merchantSales(candidates) { samples.getValue(it) }.map { it.item.id })
    }

    @Test fun equalValuesAtTheBatchLimitFavorMoreTradingSamples() {
        val candidates = (100..107).map { quote(it, 1, 2_000) }

        assertEquals((107 downTo 101).toList(),
            BotTradeSelection.merchantSales(candidates) { it.toLong() }.map { it.item.id })
    }

    @Test fun itemsWithoutSamplesHaveStableValueAndIdentifierOrdering() {
        val candidates = listOf(quote(101, 1, 3_000), quote(100, 1, 3_000), quote(102, 1, 4_000))

        assertEquals(listOf(102, 100, 101),
            BotTradeSelection.merchantSales(candidates) { 0 }.map { it.item.id })
    }

    @Test fun stackValuesAboveTheIntegerLimitRemainDistinctWhenChoosingSeven() {
        val candidates = (100..107).map { quote(it, 2, 1_500_000_000L + (it - 100) * 100_000_000L) }

        assertEquals((107 downTo 101).toList(),
            BotTradeSelection.merchantSales(candidates) { 0 }.map { it.item.id })
    }

    @Test fun overflowingStackValuesCannotBecomeCheapOrNegative() {
        val candidates = listOf(quote(100, Int.MAX_VALUE, Long.MAX_VALUE), quote(101, 2, 3_000_000_000L))

        assertEquals(listOf(100, 101),
            BotTradeSelection.merchantSales(candidates) { 0 }.map { it.item.id })
    }

    @Test fun aDepletedBatchCanBeReplacedByOtherEligibleStock() {
        val stock = (100..108).map { quote(it, 1, 10_000L - (it - 100) * 1_000) }
        val firstBatch = BotTradeSelection.merchantSales(stock) { 0 }
        val secondBatch = BotTradeSelection.merchantSales(stock.filterNot { it in firstBatch }) { 0 }

        assertEquals((100..106).toList(), firstBatch.map { it.item.id })
        assertEquals(listOf(107, 108), secondBatch.map { it.item.id })
        assertTrue(BotTradeSelection.merchantSales(emptyList()) { 0 }.isEmpty())
    }

    @Test fun partialSalesDoNotKeepResidualStacksBelowTheMinimumValue() {
        val remainder = listOf(quote(100, 1_000, 1), quote(101, 1_001, 1), quote(102, 0, 5_000))

        assertEquals(listOf(101), BotTradeSelection.merchantSales(remainder) { 0 }.map { it.item.id })
    }
}
