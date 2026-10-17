package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.common.collect.ImmutableSet
import game.skill.crafting.hideTanning.Hide
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.brain.BotPreference
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class TanHideBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @ParameterizedTest @EnumSource(Hide::class)
    fun unaffordableOwnedHidesRequestOnlyCoins(hide: Hide) {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(hide.hide))
        val script = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))
        assertTrue(script.withdraw().isEmpty())
        verify(bot.preferences).raiseWantedItemTarget(995, 100_000)
        for (id in Hide.HIDE_TO_HIDE.keys) verify(bot.preferences, never()).raiseWantedItemTarget(eq(id), anyInt())
    }

    @Test fun missingHidesWithAvailableCoinsDoNotCreateCoinDemand() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(995, 2))
        val script = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))
        assertTrue(script.withdraw().isEmpty())
        for (id in Hide.HIDE_TO_HIDE.keys) verify(bot.preferences).raiseWantedItemTarget(id, 500)
        verify(bot.preferences, never()).raiseWantedItemTarget(eq(995), anyInt())
        verify(bot.preferences, never()).addWantedItem(anyInt(), anyInt())
    }

    @Test fun requestsPreserveHigherTargetsAndRaiseSmallerTargetsWithoutChangingMetadata() {
        val bot = InventoryProductionFixtures.bot()
        val hides = Hide.HIDE_TO_HIDE.keys.toList()
        fun wanted(id: Int, target: Int) = WantedItemDefinition(id, 25, target, SKILL_CRAFTING, 99,
            WantedItemPriority.URGENT, ImmutableSet.of(WantedItemTag.SKILLING_CONSUMABLE))
        val higher = wanted(hides[0], 5_000)
        val smaller = wanted(hides[1], 100)
        val coins = wanted(995, 200_000)
        val preferences = BotPreference(mutableMapOf(), emptySet(), mutableMapOf(higher.id() to higher,
            smaller.id() to smaller, coins.id() to coins), emptySet(), mutableMapOf())
        `when`(bot.preferences).thenReturn(preferences)
        val script = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))
        assertTrue(script.withdraw().isEmpty())
        assertSame(higher, preferences.getWantedItem(higher.id()))
        assertSame(coins, preferences.getWantedItem(995))
        val raised = preferences.getWantedItem(smaller.id())
        assertEquals(500, raised.target())
        assertEquals(smaller.min(), raised.min())
        assertEquals(smaller.skill(), raised.skill())
        assertEquals(smaller.maxLevel(), raised.maxLevel())
        assertEquals(smaller.priority(), raised.priority())
        assertEquals(smaller.tags(), raised.tags())
    }

    @Test fun startupIncludesCarriedHidesAndCoinsAndBankingRequiresDepositedStock() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.crafting.staticLevel).thenReturn(1)
        InventoryProductionFixtures.inventory(bot, Item(Hide.SOFT_LEATHER.hide, 3), Item(995, 6))
        val script = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))
        assertTrue(script.onInit(false))
        assertEquals(listOf(Item(995, 6), Item(Hide.SOFT_LEATHER.hide, 3)), script.withdraw())
        verifyNoInteractions(bot.preferences)
        val items = listOf(Item(Hide.SOFT_LEATHER.hide, 3), Item(995, 6))
        for (item in items) {
            assertTrue(bot.inventory.remove(item))
            assertTrue(bot.bank.add(item))
        }
        val banking = bot.actionHandler.banking
        `when`(banking.withdrawAll(listOf(Item(995, 6), Item(Hide.SOFT_LEATHER.hide, 3)))).thenReturn(true)
        script.onBankOpen(true)
        verify(banking).withdrawAll(listOf(Item(995, 6), Item(Hide.SOFT_LEATHER.hide, 3)))
    }

    @Test fun highLevelCowhideWithOnlyTheSoftFeeAlwaysRemainsAffordable() {
        repeat(100) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.bank(bot, Item(Hide.SOFT_LEATHER.hide), Item(995, 2))
            val script = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))
            assertEquals(listOf(Item(995, 2), Item(Hide.SOFT_LEATHER.hide)), script.withdraw())
            assertFalse(script.isTerminated())
            verifyNoInteractions(bot.preferences)
        }
    }

    @Test fun anAffordableOwnedRecipeAvoidsRequestsForOtherExpensiveHides() {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.crafting.staticLevel).thenReturn(1)
        InventoryProductionFixtures.bank(bot, Item(Hide.BLACK_D_LEATHER.hide, 100),
            Item(Hide.SOFT_LEATHER.hide, 5), Item(995, 9))
        val script = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))
        assertEquals(listOf(Item(995, 8), Item(Hide.SOFT_LEATHER.hide, 4)), script.withdraw())
        verifyNoInteractions(bot.preferences)
    }

    @Test fun exhaustedBankStockDoesNotRequestAlreadyOwnedInventorySupplies() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(Hide.SNAKESKIN.hide), Item(995, Hide.SNAKESKIN.cost))
        val script = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))
        script.onBankOpen(true)
        assertTrue(script.isTerminated())
        verifyNoInteractions(bot.preferences)
        verify(bot.actionHandler.banking, never()).withdrawAll(anyList())
    }
}
