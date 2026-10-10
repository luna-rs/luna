package game.skill.crafting.glassMaking

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import io.luna.game.model.item.Item
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class MakeMoltenGlassActionItemTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun fullBatchReturnsEveryBucketAndAwardsExperienceOnlyForConversions() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1781, 14), Item(1783, 14))
        assertTrue(bot.inventory.isFull)
        val action = MakeMoltenGlassActionItem(bot, 14)
        bot.actions.submit(action)
        repeat(13) { assertFalse(action.run()) }
        assertTrue(action.run())
        assertEquals(14, bot.inventory.computeAmountForId(1775))
        assertEquals(14, bot.inventory.computeAmountForId(1925))
        assertFalse(bot.inventory.contains(1781))
        assertFalse(bot.inventory.contains(1783))
        assertTrue(bot.inventory.isFull)
        verify(bot.crafting, times(14)).addExperience(20.0)
    }

    @Test fun missingEitherIngredientLeavesTheInventoryAndExperienceUnchanged() {
        for (remaining in listOf(1781, 1783)) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(remaining))
            InventoryProductionFixtures.execute(bot, MakeMoltenGlassActionItem(bot, 1))
            assertEquals(1, bot.inventory.computeAmountForId(remaining))
            assertFalse(bot.inventory.contains(1775))
            assertFalse(bot.inventory.contains(1925))
            verify(bot.crafting, never()).addExperience(anyDouble())
        }
    }

    @Test fun partialBatchStopsAtTheFirstExhaustedIngredientAndPreservesSurplus() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1781, 3), Item(1783, 2), Item(1925))
        val action = MakeMoltenGlassActionItem(bot, 14)
        bot.actions.submit(action)
        repeat(2) { assertFalse(action.run()) }
        assertTrue(action.run())
        assertEquals(2, bot.inventory.computeAmountForId(1775))
        assertEquals(3, bot.inventory.computeAmountForId(1925))
        assertEquals(1, bot.inventory.computeAmountForId(1781))
        assertFalse(bot.inventory.contains(1783))
        verify(bot.crafting, times(2)).addExperience(20.0)
    }
}
