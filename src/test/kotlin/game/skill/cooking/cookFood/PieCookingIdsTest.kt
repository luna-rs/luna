package game.skill.cooking.cookFood

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.cooking.prepareFood.IncompleteFood
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.`object`.GameObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class PieCookingIdsTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun cookingLookupMatchesPreparationOutputsAndLunaItemDefinitions() {
        assertEquals(IncompleteFood.UNCOOKED_MEAT_PIE.id, Food.MEAT_PIE.raw)
        assertEquals(IncompleteFood.UNCOOKED_APPLE_PIE.id, Food.APPLE_PIE.raw)
        assertEquals(Food.MEAT_PIE, Food.RAW_TO_FOOD[2319])
        assertEquals(Food.APPLE_PIE, Food.RAW_TO_FOOD[2317])
        assertEquals("Uncooked meat pie", itemName(Food.MEAT_PIE.raw))
        assertEquals("Uncooked apple pie", itemName(Food.APPLE_PIE.raw))
        assertEquals("Meat pie", itemName(Food.MEAT_PIE.cooked))
        assertEquals("Apple pie", itemName(Food.APPLE_PIE.cooked))
        assertEquals("Raw mud pie", itemName(IncompleteFood.RAW_MUD_PIE.id))
        assertNull(Food.RAW_TO_FOOD[IncompleteFood.RAW_MUD_PIE.id])
    }

    @Test fun actualCookingConsumesMatchingRawPiesProducesMatchingCookedPiesAndPreservesMudPie() {
        val bot = InventoryProductionFixtures.bot()
        // Reach the existing burn-stop threshold in this fixture to make the success path deterministic.
        `when`(bot.cooking.level).thenReturn(120)
        InventoryProductionFixtures.inventory(bot, Item(2319), Item(2317), Item(7168))
        val range = mock(GameObject::class.java)
        `when`(range.position).thenReturn(Position(3200, 3200))
        InventoryProductionFixtures.execute(bot, CookFoodActionItem(bot, range, Food.MEAT_PIE, false, 1))
        assertFalse(bot.inventory.contains(2319))
        assertTrue(bot.inventory.contains(2327))
        assertTrue(bot.inventory.contains(2317))
        assertTrue(bot.inventory.contains(7168))
        verify(bot.cooking).addExperience(104.0)
        InventoryProductionFixtures.execute(bot, CookFoodActionItem(bot, range, Food.APPLE_PIE, false, 1))
        assertFalse(bot.inventory.contains(2317))
        assertTrue(bot.inventory.contains(2323))
        assertTrue(bot.inventory.contains(7168))
        verify(bot.cooking).addExperience(130.0)
    }

    @Test fun insufficientLevelsDoNotConsumeRawPiesOrAwardExperience() {
        for (food in listOf(Food.MEAT_PIE, Food.APPLE_PIE)) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.cooking.level).thenReturn(food.lvl - 1)
            InventoryProductionFixtures.inventory(bot, Item(food.raw))
            val action = CookFoodActionItem(bot, mock(GameObject::class.java), food, false, 1)
            bot.actions.submit(action)
            assertTrue(action.run())
            assertTrue(bot.inventory.contains(food.raw))
            assertFalse(bot.inventory.contains(food.cooked))
            verify(bot.cooking, never()).addExperience(anyDouble())
        }
    }
}