package game.skill.cooking.prepareFood

import api.bot.script.InventoryProductionFixtures
import api.event.Matcher
import api.predef.*
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.ListMultimap
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

class StewRecipeRegistrationTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun compiledRegistrationOpensAndMakesBothStewRecipesInEitherItemOrder() {
        val matcher = Matcher.get<ItemOnItemEvent, Pair<Int, Int>>()
        val field = Matcher::class.java.getDeclaredField("actions").apply { isAccessible = true }
        val actions = field.get(matcher) as ListMultimap<Pair<Int, Int>, EventMatcherListener<ItemOnItemEvent>>
        val previous = ArrayListMultimap.create(actions)
        val previousMatchers = scriptMatchers.size
        val dispatch = Matcher::class.java.getDeclaredMethod("match", Event::class.java).apply { isAccessible = true }
        try {
            actions.clear()
            Class.forName("game.skill.cooking.prepareFood.PrepareFood")
                .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
            for (food in IncompleteFood.entries) for (secondary in food.otherIngredients) {
                assertTrue(food.baseIngredient to secondary in matcher.keys(), food.name)
                assertTrue(secondary to food.baseIngredient in matcher.keys(), food.name)
            }
            assertFalse(1980 to 1927 in matcher.keys())
            assertFalse(1927 to 1980 in matcher.keys())
            assertTrue(4242 to 1927 in matcher.keys())
            assertTrue(1927 to 4242 in matcher.keys())
            for (food in listOf(IncompleteFood.UNCOOKED_STEW_FROM_POTATO, IncompleteFood.UNCOOKED_STEW_FROM_MEAT)) {
                for (secondary in food.otherIngredients) for (reverse in listOf(false, true)) {
                    val bot = InventoryProductionFixtures.bot()
                    `when`(bot.cooking.level).thenReturn(25)
                    InventoryProductionFixtures.inventory(bot, Item(food.baseIngredient), Item(secondary))
                    val event = ItemOnItemEvent(bot, if (reverse) secondary else food.baseIngredient,
                        if (reverse) food.baseIngredient else secondary, 0, 1, 0, 0)
                    assertTrue(matcher.has(event))
                    assertEquals(true, dispatch.invoke(matcher, event))
                    val dialogue = ArgumentCaptor.forClass(MakeItemDialogue::class.java)
                    verify(bot.overlays).open(dialogue.capture())
                    dialogue.value.makeIndex(bot, 0, 1)
                    val submitted = ArgumentCaptor.forClass(Action::class.java)
                    verify(bot).submitAction(submitted.capture())
                    val action = submitted.value as PrepareFoodActionItem
                    assertEquals(food, action.food)
                    InventoryProductionFixtures.execute(bot, action)
                    assertFalse(bot.inventory.contains(food.baseIngredient))
                    assertFalse(bot.inventory.contains(secondary))
                    assertEquals(1, bot.inventory.computeAmountForId(2001))
                    verify(bot.cooking, never()).addExperience(anyDouble())
                }
            }
        } finally {
            actions.clear()
            actions.putAll(previous)
            scriptMatchers.subList(previousMatchers, scriptMatchers.size).clear()
        }
    }
}
