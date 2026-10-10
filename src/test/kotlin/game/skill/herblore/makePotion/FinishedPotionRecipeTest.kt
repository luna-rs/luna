package game.skill.herblore.makePotion

import api.bot.script.InventoryProductionFixtures
import api.event.Matcher
import api.predef.*
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
import game.player.item.consume.potion.Potion
import game.skill.herblore.makeUnfPotion.UnfPotion
import io.luna.game.action.Action
import io.luna.game.event.Event
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class FinishedPotionRecipeTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private val corrected = listOf(FinishedPotion.ATTACK_POTION, FinishedPotion.ANTIPOISON,
        FinishedPotion.ZAMORAK_BREW, FinishedPotion.SARADOMIN_BREW)

    @Test fun correctedRecipesAgreeWithExistingIngredientAndConsumableTables() {
        val expected = listOf(
            Triple(FinishedPotion.ATTACK_POTION, UnfPotion.GUAM, Potion.ATTACK_POTION),
            Triple(FinishedPotion.ANTIPOISON, UnfPotion.MARRENTILL, Potion.ANTIPOISON_POTION),
            Triple(FinishedPotion.ZAMORAK_BREW, UnfPotion.TORSTOL, Potion.ZAMORAK_BREW),
            Triple(FinishedPotion.SARADOMIN_BREW, UnfPotion.TOADFLAX, Potion.SARADOMIN_BREW))
        for ((recipe, unf, drink) in expected) {
            assertEquals(unf.id, recipe.unf)
            assertEquals(drink.threeDose, recipe.id)
        }
        assertEquals("Eye of newt", itemName(FinishedPotion.ATTACK_POTION.secondary))
        assertEquals("Unicorn horn dust", itemName(FinishedPotion.ANTIPOISON.secondary))
        assertEquals("Jangerberries", itemName(FinishedPotion.ZAMORAK_BREW.secondary))
        assertEquals("Crushed nest", itemName(FinishedPotion.SARADOMIN_BREW.secondary))
        for (recipe in FinishedPotion.entries) assertTrue(UnfPotion.entries.any { it.id == recipe.unf })
    }

    @Test fun fullBatchesProduceCorrectItemsAndAwardExistingExperience() {
        for (recipe in FinishedPotion.entries) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.herblore.level).thenReturn(recipe.level)
            InventoryProductionFixtures.inventory(bot, Item(recipe.unf, 14), Item(recipe.secondary, 14))
            assertTrue(bot.inventory.isFull)
            val action = MakePotionActionItem(bot, recipe, 14)
            bot.actions.submit(action)
            repeat(13) { assertFalse(action.run()) }
            assertTrue(action.run())
            assertFalse(bot.inventory.contains(recipe.unf))
            assertFalse(bot.inventory.contains(recipe.secondary))
            assertEquals(14, bot.inventory.computeAmountForId(recipe.id))
            verify(bot.herblore, times(14)).addExperience(recipe.exp)
        }
    }

    @Test fun missingIngredientsOrLowLevelsPreserveSuppliesAndAwardNoExperience() {
        for (recipe in corrected) for (missing in listOf(recipe.unf, recipe.secondary, null)) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.herblore.level).thenReturn(if (missing == null) recipe.level - 1 else recipe.level)
            val supplied = listOf(recipe.unf, recipe.secondary).filter { it != missing }
            InventoryProductionFixtures.inventory(bot, *supplied.map { Item(it) }.toTypedArray())
            InventoryProductionFixtures.execute(bot, MakePotionActionItem(bot, recipe, 1))
            for (id in supplied) assertEquals(1, bot.inventory.computeAmountForId(id))
            assertFalse(bot.inventory.contains(recipe.id))
            verify(bot.herblore, never()).addExperience(anyDouble())
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun compiledRegistrationUsesCorrectIngredientsAndMakesCorrectProductsInEitherOrder() {
        val matcher = Matcher.get<ItemOnItemEvent, Pair<Int, Int>>()
        val field = Matcher::class.java.getDeclaredField("actions").apply { isAccessible = true }
        val actions = field.get(matcher) as ListMultimap<Pair<Int, Int>, EventMatcherListener<ItemOnItemEvent>>
        val previous = ArrayListMultimap.create(actions)
        val previousMatchers = scriptMatchers.size
        val dispatch = Matcher::class.java.getDeclaredMethod("match", Event::class.java).apply { isAccessible = true }
        try {
            actions.clear()
            Class.forName("game.skill.herblore.makePotion.MakePotion")
                .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
            for (recipe in FinishedPotion.entries) {
                assertTrue(recipe.unf to recipe.secondary in matcher.keys(), recipe.name)
                assertTrue(recipe.secondary to recipe.unf in matcher.keys(), recipe.name)
            }
            for (oldPair in listOf(91 to 121, 91 to 235, 75 to 247)) {
                assertFalse(oldPair in matcher.keys())
                assertFalse(oldPair.second to oldPair.first in matcher.keys())
            }
            for (recipe in corrected) for (reverse in listOf(false, true)) {
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.herblore.level).thenReturn(recipe.level)
                InventoryProductionFixtures.inventory(bot, recipe.unfItem, recipe.secondaryItem)
                val event = ItemOnItemEvent(bot, if (reverse) recipe.secondary else recipe.unf,
                    if (reverse) recipe.unf else recipe.secondary, 0, 1, 0, 0)
                assertEquals(true, dispatch.invoke(matcher, event))
                val dialogue = ArgumentCaptor.forClass(MakeItemDialogue::class.java)
                verify(bot.overlays).open(dialogue.capture())
                dialogue.value.makeIndex(bot, 0, 1)
                val submitted = ArgumentCaptor.forClass(Action::class.java)
                verify(bot).submitAction(submitted.capture())
                val action = submitted.value as MakePotionActionItem
                assertEquals(recipe, action.potion)
                InventoryProductionFixtures.execute(bot, action)
                assertEquals(1, bot.inventory.computeAmountForId(recipe.id))
                assertFalse(bot.inventory.contains(recipe.unf))
                assertFalse(bot.inventory.contains(recipe.secondary))
                verify(bot.herblore).addExperience(recipe.exp)
            }
        } finally {
            actions.clear()
            actions.putAll(previous)
            scriptMatchers.subList(previousMatchers, scriptMatchers.size).clear()
        }
    }
}
