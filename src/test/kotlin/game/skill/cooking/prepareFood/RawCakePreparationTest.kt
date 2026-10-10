package game.skill.cooking.prepareFood

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import io.luna.game.model.item.Item
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/** Regression coverage for complete raw-cake ingredient consumption using the real inventory action. */
class RawCakePreparationTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private val food = IncompleteFood.UNCOOKED_CAKE
    private val ingredients = listOf(food.baseIngredient) + food.otherIngredients

    @Test fun everyIngredientIncludingTheTinIsRequiredWithoutPartialConsumption() {
        for (missing in ingredients) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.cooking.level).thenReturn(food.lvl)
            val supplied = ingredients.filter { it != missing }
            InventoryProductionFixtures.inventory(bot, *supplied.map { Item(it) }.toTypedArray())
            InventoryProductionFixtures.execute(bot,
                PrepareFoodActionItem(bot, food, mutableSetOf(1887, 1944), 1))
            for (id in supplied) assertEquals(1, bot.inventory.computeAmountForId(id))
            for (id in listOf(food.id, 1925, 1931)) assertFalse(bot.inventory.contains(id))
            verify(bot.cooking, never()).addExperience(anyDouble())
        }
    }

    @Test fun fullBatchConsumesSevenTinsAndReturnsOnlyBucketsAndPots() {
        for (secondary in food.otherIngredients) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.cooking.level).thenReturn(food.lvl)
            InventoryProductionFixtures.inventory(bot, *ingredients.map { Item(it, 7) }.toTypedArray())
            assertTrue(bot.inventory.isFull)
            val action = PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, secondary), 7)
            bot.actions.submit(action)
            repeat(6) { assertFalse(action.run()) }
            assertTrue(action.run())
            for (id in ingredients) assertFalse(bot.inventory.contains(id))
            for (id in listOf(food.id, 1925, 1931)) assertEquals(7, bot.inventory.computeAmountForId(id))
            assertEquals(7, bot.inventory.computeRemainingSize())
            verify(bot.cooking, times(7)).addExperience(food.exp)
        }
    }

    @Test fun oneTinCannotProduceTwoCakesFromTwoSetsOfIngredients() {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.cooking.level).thenReturn(food.lvl)
        InventoryProductionFixtures.inventory(bot, Item(food.baseIngredient),
            *food.otherIngredients.map { Item(it, 2) }.toTypedArray())
        val action = PrepareFoodActionItem(bot, food, mutableSetOf(1887, 1927), 2)
        bot.actions.submit(action)
        assertFalse(action.run())
        assertTrue(action.run())
        assertFalse(bot.inventory.contains(food.baseIngredient))
        for (id in food.otherIngredients) assertEquals(1, bot.inventory.computeAmountForId(id))
        for (id in listOf(food.id, 1925, 1931)) assertEquals(1, bot.inventory.computeAmountForId(id))
        verify(bot.cooking, times(1)).addExperience(food.exp)
    }

    @Test fun insufficientCookingLevelPreservesAllIngredientsAndAwardsNoExperience() {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.cooking.level).thenReturn(food.lvl - 1)
        InventoryProductionFixtures.inventory(bot, *ingredients.map { Item(it) }.toTypedArray())
        InventoryProductionFixtures.execute(bot,
            PrepareFoodActionItem(bot, food, mutableSetOf(1887, 1933), 1))
        for (id in ingredients) assertEquals(1, bot.inventory.computeAmountForId(id))
        for (id in listOf(food.id, 1925, 1931)) assertFalse(bot.inventory.contains(id))
        verify(bot.cooking, never()).addExperience(anyDouble())
    }
}
