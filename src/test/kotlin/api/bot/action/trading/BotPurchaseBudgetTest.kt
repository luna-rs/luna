package api.bot.action.trading

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import api.predef.ext.wantedItemsToAcquire
import api.predef.ext.canUseWantedItem
import com.google.common.collect.Iterators
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Regression coverage for affordable purchase intentions and usable equipment priority.
 *
 * @author lare96
 */
class BotPurchaseBudgetTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun fixtures() = InventoryProductionFixtures.initializeCombat()
    }

    private val bot = InventoryProductionFixtures.bot()

    private fun quote(id: Int, amount: Int, price: Long, owner: Bot = bot) =
        BotTradeItem(Item(id, amount), price, BotTradePurpose.BUY, owner)

    @Test
    fun expensiveWeaponIsSkippedAndAffordableSuppliesRemain() {
        val selected = BotTradeManager.affordablePurchases(
            listOf(quote(7158, 1, 226_000), quote(314, 200_000, 1)),
            listOf(quote(995, 126_000, 1)))
        assertEquals(listOf(Item(314, 126_000)), selected.map { it.item })
    }

    @Test
    fun candidatesShareOneBudgetInPriorityOrder() {
        val selected = BotTradeManager.affordablePurchases(
            listOf(quote(1511, 2, 30), quote(314, 100, 1)), listOf(quote(995, 100, 1)))
        assertEquals(listOf(Item(1511, 2), Item(314, 40)), selected.map { it.item })
        assertEquals(100, selected.sumOf { it.totalPrice() })
    }

    @Test
    fun emptyPaymentAndPricesLargerThanAvailableFundsCannotProduceDemand() {
        val wanted = listOf(quote(7158, Int.MAX_VALUE, Long.MAX_VALUE))
        assertTrue(BotTradeManager.affordablePurchases(wanted, emptyList()).isEmpty())
        assertTrue(BotTradeManager.affordablePurchases(wanted, listOf(quote(995, Int.MAX_VALUE, 1))).isEmpty())
    }

    @Test
    fun exactBudgetAndReducedWithdrawalsClampMerchantQuantities() {
        val selected = BotTradeManager.affordablePurchases(
            listOf(quote(314, Int.MAX_VALUE, 2)), listOf(quote(995, 100, 1)))
        assertEquals(50, selected.single().item.amount)
        val carried = BotTradeManager.affordablePurchases(selected, listOf(quote(995, 9, 1)))
        assertEquals(4, carried.single().item.amount)
    }

    @Test
    fun barterUsesWholeItemsAndCannotSpendThePurchasedCommodity() {
        val selected = BotTradeManager.affordablePurchases(
            listOf(quote(314, 100, 1)), listOf(quote(1511, 1, 50)))
        assertEquals(Item(314, 50), selected.single().item)
        assertTrue(BotTradeManager.affordablePurchases(
            listOf(quote(314, 10, 1)), listOf(quote(1511, 1, 50))).isEmpty())
        assertTrue(BotTradeManager.affordablePurchases(
            listOf(quote(314, 100, 1)), listOf(quote(314, 100, 1))).isEmpty())
    }

    @Test
    fun toolsUseSkillingLevelsRatherThanWieldingRequirements() {
        `when`(bot.mining.staticLevel).thenReturn(40)
        assertFalse(bot.canUseWantedItem(1275))
        `when`(bot.mining.staticLevel).thenReturn(41)
        assertTrue(bot.canUseWantedItem(1275))
        `when`(bot.woodcutting.staticLevel).thenReturn(40)
        assertFalse(bot.canUseWantedItem(1359))
        `when`(bot.woodcutting.staticLevel).thenReturn(41)
        assertTrue(bot.canUseWantedItem(1359))
    }

    @Test
    fun usableNeedsPrecedeUnusableUpgradesUntilRequirementsAreMet() {
        val id = 7158
        val equipment = EquipmentDefinition.ALL.retrieve(id)
        assertTrue(equipment.requirements.isNotEmpty())
        equipment.requirements.forEach { requirement ->
            `when`(bot.skill(requirement.id).level).thenReturn(requirement.level - 1)
        }
        val upgrade = WantedItemDefinition(id, -1, 1, -1, -1,
            WantedItemPriority.URGENT, WantedItemDefinition.DEFAULT_TAGS)
        val supplies = WantedItemDefinition(314, -1, 100, -1, -1,
            WantedItemPriority.LOW, WantedItemDefinition.DEFAULT_TAGS)
        val preferences = bot.preferences
        `when`(preferences.bot).thenReturn(bot)
        `when`(preferences.wantedItems).thenAnswer {
            Iterators.unmodifiableIterator(listOf(upgrade, supplies).iterator())
        }
        assertEquals(listOf(314, id), preferences.wantedItemsToAcquire().map { it.id })
        equipment.requirements.forEach { requirement ->
            `when`(bot.skill(requirement.id).level).thenReturn(requirement.level)
        }
        assertEquals(listOf(id, 314), preferences.wantedItemsToAcquire().map { it.id })
    }
}
