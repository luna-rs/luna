package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import game.bot.scripts.skills.CookFoodBotScript.Companion.CookFoodData
import game.skill.cooking.cookFood.Food
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.game.model.mob.bot.script.BotScriptStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class CookFoodBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.cooking.staticLevel).thenReturn(99)
    }

    private fun registry(): BotScriptManager {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try { InventoryProductionFixtures.register("Scripts") }
        finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
        return registry
    }

    @ParameterizedTest @EnumSource(Food::class)
    fun realStackRoundTripKeepsEveryExplicitFoodAndSessionConfiguration(food: Food) {
        val bot = bot()
        val registry = registry()
        val zones = mutableListOf(SubZone.AL_KHARID_BANK, SubZone.VARROCK_EAST_BANK)
        val script = CookFoodBotScript(bot, food, 7.minutes, zones)
        val stack = BotScriptStack(bot, registry)
        stack.pushTail(script)
        val saved = stack.save()
        assertEquals(CookFoodData::class.java.name, saved.single().scriptDataClass)
        val restoredStack = BotScriptStack(bot, registry)
        restoredStack.load(saved)
        assertEquals(1, restoredStack.size())
        val restored = restoredStack.current() as CookFoodBotScript
        assertEquals(food, restored.selectedFood)
        assertEquals(food, restored.snapshot().food)
        assertEquals(7.minutes, restored.snapshot().duration)
        assertEquals(zones, restored.snapshot().zones)
    }

    @Test fun restoredExplicitRecipeDoesNotSwitchToEarlierOwnedFood() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Food.BREAD.raw), Item(Food.SHARK.raw, 3))
        val original = CookFoodBotScript(bot, Food.SHARK, 10.minutes, mutableListOf(SubZone.AL_KHARID_BANK))
        val json = JsonObject().also { original.snapshot().save(it) }
        val data = CookFoodData().apply { load(json) }
        val restored = CookFoodBotScript(bot, data)
        assertEquals(listOf(Item(Food.SHARK.raw, 3)), restored.withdraw())
        bot.bank.remove(Item(Food.SHARK.raw, 3))
        val exhausted = InventoryProductionFixtures.active(CookFoodBotScript(bot, data))
        assertTrue(exhausted.withdraw().isEmpty())
        assertTrue(exhausted.isTerminated())
        assertTrue(bot.bank.contains(Food.BREAD.raw))
    }

    @Test fun legacyDataClassRestoresThroughTheUnchangedCentralRegistration() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Food.BREAD.raw, 2))
        val legacy = ZonedBotScriptData().apply {
            duration = 5.minutes
            zones = mutableListOf(SubZone.VARROCK_EAST_BANK)
        }
        val json = JsonObject().also { legacy.save(it) }
        val stack = BotScriptStack(bot, registry())
        stack.load(listOf(BotScriptSnapshot(0, CookFoodBotScript::class.java.name,
            ZonedBotScriptData::class.java.name, json)))
        assertEquals(1, stack.size())
        val restored = stack.current() as CookFoodBotScript
        assertNull(restored.selectedFood)
        assertEquals(legacy.zones, restored.snapshot().zones)
        assertEquals(legacy.duration, restored.snapshot().duration)
        assertEquals(listOf(Item(Food.BREAD.raw, 2)), restored.withdraw())
    }

    @Test fun automaticSelectionRemainsAutomaticAfterSavingAnActiveRecipe() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Food.BREAD.raw))
        val original = CookFoodBotScript(bot, null, 10.minutes, mutableListOf(SubZone.AL_KHARID_BANK))
        assertEquals(listOf(Item(Food.BREAD.raw)), original.withdraw())
        val data = original.snapshot()
        assertNull(data.food)
        val stack = BotScriptStack(bot, registry())
        stack.pushTail(original)
        val saved = stack.save()
        bot.bank.remove(Food.BREAD.raw)
        InventoryProductionFixtures.bank(bot, Item(Food.SHARK.raw))
        val restoredStack = BotScriptStack(bot, registry())
        restoredStack.load(saved)
        val restored = restoredStack.current() as CookFoodBotScript
        assertNull(restored.selectedFood)
        assertEquals(listOf(Item(Food.SHARK.raw)), restored.withdraw())
    }

    @Test fun absentAndNullRecipeFieldsRetainLegacyAutomaticSelection() {
        val data = CookFoodData().apply {
            food = Food.LOBSTER
            duration = 4.minutes
            zones = mutableListOf(SubZone.AL_KHARID_BANK)
        }
        val json = JsonObject().also { data.save(it) }
        assertEquals(Food.LOBSTER.name, json.get("food").asString)
        json.remove("food")
        data.load(json)
        assertNull(data.food)
        json.add("food", JsonNull.INSTANCE)
        data.load(json)
        assertNull(data.food)
        assertEquals(4.minutes, data.duration)
        assertEquals(listOf(SubZone.AL_KHARID_BANK), data.zones)
    }

    @Test fun restoredRecipeStillRequiresItsExistingPermanentLevel() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Food.SHARK.raw))
        val data = CookFoodData().apply {
            food = Food.SHARK
            duration = 5.minutes
            zones = mutableListOf(SubZone.AL_KHARID_BANK)
        }
        `when`(bot.cooking.staticLevel).thenReturn(Food.SHARK.lvl - 1)
        val script = InventoryProductionFixtures.active(CookFoodBotScript(bot, data))
        assertTrue(script.withdraw().isEmpty())
        assertTrue(script.isTerminated())
        assertEquals(Food.SHARK, script.snapshot().food)
    }
}
