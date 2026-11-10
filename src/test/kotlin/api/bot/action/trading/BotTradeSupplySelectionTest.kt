package api.bot.action.trading

import engine.bot.coordinator.skill.ScriptFixtures
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.util.parser.impl.WantedItemDefinitionFileParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.random.Random

/**
 * Verifies common supply choices stay dominant even when a large catalogue of costly niche items is eligible.
 *
 * @author lare96
 */
class BotTradeSupplySelectionTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun fixtures() {
            ScriptFixtures.initialize()
            if (WantedItemDefinition.DEFAULT.size == 0) WantedItemDefinitionFileParser().run()
        }
    }

    @Test
    fun commonSupplyShareIsIndependentOfTheNumberOfNicheCandidates() {
        val random = Random(20261109)
        val candidates = listOf(2, 1) + List(1_000) { 0 }
        val first = List(5_000) { BotTradeSelection.supplyOrder(candidates, random) { it }.first() }
        val supplyShare = first.count { it > 0 }.toDouble() / first.size
        val skillingShare = first.count { it == 2 }.toDouble() / first.count { it > 0 }

        assertTrue(supplyShare in 0.93..0.97, "supply share=$supplyShare")
        assertTrue(skillingShare in 0.73..0.77, "skilling share=$skillingShare")
        assertTrue(first.any { it == 0 }, "Equipment and miscellaneous items remain eligible")
    }

    @Test
    fun depletedCategoriesFallBackAndRetainEachCategoryOrder() {
        val items = listOf(20, 0, 10, 21, 1, 11, 22)
        val ordered = BotTradeSelection.supplyOrder(items, Random(42)) { it / 10 }

        assertEquals(items.toSet(), ordered.toSet())
        for (category in 0..2) {
            assertEquals(items.filter { it / 10 == category }, ordered.filter { it / 10 == category })
        }
        assertEquals(listOf(9, 8, 7), BotTradeSelection.supplyOrder(listOf(9, 8, 7)) { 0 })
        assertTrue(BotTradeSelection.supplyOrder(emptyList<Int>()) { 2 }.isEmpty())
    }

    @Test
    fun actualCatalogueSeparatesRawMaterialsRunesAmmoAndReusableEquipment() {
        for (id in listOf(314, 1511, 436, 2353, 249, 1617, 1777, 556)) {
            assertEquals(2, BotTradeSelection.supplyPriority(id), "skilling item=$id")
        }
        for (id in listOf(882, 884, 886, 2434)) {
            assertEquals(1, BotTradeSelection.supplyPriority(id), "combat item=$id")
        }
        for (id in listOf(1359, 1123, 1011, 4127, 90_000)) {
            assertEquals(0, BotTradeSelection.supplyPriority(id), "occasional item=$id")
        }
    }

    @Test
    fun commonStockCanEnterMerchantBatchAheadOfExtremelyValuableEquipment() {
        val bot = mock(Bot::class.java)
        fun quote(id: Int, price: Long) = BotTradeItem(Item(id, 2_000), price,
            BotTradePurpose.MERCHANT_SELL, bot)
        val supplies = listOf(quote(436, 1), quote(882, 1))
        val niche = (7700..7710).map { quote(it, 1_000_000) }
        val selected = BotTradeSelection.merchantSales(supplies + niche, Random(0)) { 0 }

        assertEquals(7, selected.size)
        assertTrue(selected.containsAll(supplies))
        assertSame(supplies.first { it.item.id == selected.first().item.id }, selected.first())
    }
}
