package game.skill.crafting.jewelleryMaking

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.smithing.BarType
import io.luna.game.model.item.Item
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class CraftJewelleryActionTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun recipes(): List<Triple<BarType, Int, JewelleryItem>> =
        GoldJewelleryTable.VALUES.flatMap { table ->
            table.jewelleryItems.map { Triple(BarType.GOLD, table.mouldId, it) }
        } + SilverJewelleryTable.VALUES.map { Triple(BarType.SILVER, it.mouldId, it.jewelleryItem) }

    @Test fun everyRecipeRequiresItsExactMouldAndCannotUseAnotherMould() {
        for ((bar, mould, recipe) in recipes()) for (wrongMould in listOf<Int?>(null,
            (GoldJewelleryTable.MOULDS + SilverJewelleryTable.MOULDS).first { it != mould })) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(bar.id))
            recipe.requiredItem?.let { InventoryProductionFixtures.inventory(bot, it) }
            wrongMould?.let { InventoryProductionFixtures.inventory(bot, Item(it)) }
            InventoryProductionFixtures.execute(bot, CraftJewelleryAction(bot, bar, recipe, 1))
            assertTrue(bot.inventory.contains(bar.id))
            recipe.requiredItem?.let { assertTrue(bot.inventory.contains(it.id)) }
            wrongMould?.let { assertTrue(bot.inventory.contains(it)) }
            assertFalse(bot.inventory.contains(recipe.id))
            verify(bot.crafting, never()).addExperience(anyDouble())
        }
    }

    @Test fun allGoldAndSilverRecipesConvertFullBatchesAndRetainTheirMould() {
        for ((bar, mould, recipe) in recipes()) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.crafting.level).thenReturn(recipe.level)
            val amount = if (recipe.requiredItem == null) 27 else 13
            InventoryProductionFixtures.inventory(bot, Item(mould), Item(bar.id, amount))
            recipe.requiredItem?.let {
                InventoryProductionFixtures.inventory(bot, Item(it.id, amount), Item(1925))
            }
            assertTrue(bot.inventory.isFull)
            val action = CraftJewelleryAction(bot, bar, recipe, amount)
            bot.actions.submit(action)
            repeat(amount - 1) { assertFalse(action.run()) }
            assertTrue(action.run())
            assertEquals(amount, bot.inventory.computeAmountForId(recipe.id))
            assertEquals(1, bot.inventory.computeAmountForId(mould))
            assertFalse(bot.inventory.contains(bar.id))
            recipe.requiredItem?.let { assertFalse(bot.inventory.contains(it.id)) }
            verify(bot.crafting, times(amount)).addExperience(recipe.xp)
        }
    }

    @Test fun removingTheMouldDuringAnActiveBatchStopsBeforeAnotherConversion() {
        for ((bar, mould, recipe) in recipes()) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(mould), Item(bar.id, 2))
            recipe.requiredItem?.let { InventoryProductionFixtures.inventory(bot, Item(it.id, 2)) }
            val action = CraftJewelleryAction(bot, bar, recipe, 2)
            bot.actions.submit(action)
            assertFalse(action.run())
            assertTrue(bot.inventory.remove(mould))
            assertTrue(action.run())
            assertEquals(1, bot.inventory.computeAmountForId(recipe.id))
            assertEquals(1, bot.inventory.computeAmountForId(bar.id))
            recipe.requiredItem?.let { assertEquals(1, bot.inventory.computeAmountForId(it.id)) }
            verify(bot.crafting).addExperience(recipe.xp)
        }
    }

    @Test fun mismatchedMetalCannotCraftRecipesFromTheOtherTable() {
        for ((bar, mould, recipe) in recipes()) {
            val wrongBar = if (bar == BarType.GOLD) BarType.SILVER else BarType.GOLD
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(mould), Item(wrongBar.id))
            recipe.requiredItem?.let { InventoryProductionFixtures.inventory(bot, it) }
            InventoryProductionFixtures.execute(bot, CraftJewelleryAction(bot, wrongBar, recipe, 1))
            assertTrue(bot.inventory.contains(mould))
            assertTrue(bot.inventory.contains(wrongBar.id))
            assertFalse(bot.inventory.contains(recipe.id))
            verify(bot.crafting, never()).addExperience(anyDouble())
        }
    }

    @Test fun lowLevelAndMissingSuppliesStillPreventConversionWithTheCorrectMould() {
        for ((bar, mould, recipe) in recipes()) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(mould), Item(bar.id))
            recipe.requiredItem?.let { InventoryProductionFixtures.inventory(bot, it) }
            `when`(bot.crafting.level).thenReturn(recipe.level - 1)
            InventoryProductionFixtures.execute(bot, CraftJewelleryAction(bot, bar, recipe, 1))
            assertTrue(bot.inventory.contains(bar.id))
            `when`(bot.crafting.level).thenReturn(recipe.level)
            bot.inventory.remove(bar.id)
            InventoryProductionFixtures.execute(bot, CraftJewelleryAction(bot, bar, recipe, 1))
            assertTrue(bot.inventory.contains(mould))
            assertFalse(bot.inventory.contains(recipe.id))
            verify(bot.crafting, never()).addExperience(anyDouble())
        }
    }
}
