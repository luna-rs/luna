package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.common.collect.Iterators
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import engine.bot.coordinator.GeneralActivityCoordinator
import io.luna.game.model.Position
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPreference
import io.luna.game.model.`object`.GameObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class GeneralActivitiesTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun newAndLegacyProfilesHaveDefaultWeightAndExplicitOptOutSurvivesLoading() {
        val preferences = BotPreference(mutableMapOf(), mutableSetOf(), mutableMapOf(), mutableSetOf(), mutableMapOf())
        assertEquals(0.25, preferences.activities[BotActivity.GENERAL_ACTIVITIES])
        val json = preferences.save()
        json.add("activities", JsonArray())
        preferences.load(json)
        assertEquals(0.25, preferences.activities[BotActivity.GENERAL_ACTIVITIES])
        val optedOut = BotPreference(mutableMapOf(BotActivity.GENERAL_ACTIVITIES to 0.0), mutableSetOf(),
            mutableMapOf(), mutableSetOf(), mutableMapOf())
        val saved = optedOut.save()
        preferences.load(saved)
        assertEquals(0.0, preferences.activities[BotActivity.GENERAL_ACTIVITIES])
        assertSame(GeneralActivityCoordinator, BotActivity.GENERAL_ACTIVITIES.coordinator)
    }

    @Test fun waterIsSelectedWithoutClayAndStopsAtItsConfiguredTotalTarget() {
        val bot = InventoryProductionFixtures.bot()
        val preferences = BotPreference(mutableMapOf(), mutableSetOf(),
            mutableMapOf(1929 to WantedItemDefinition(1929, 500)), mutableSetOf(), mutableMapOf())
        `when`(bot.preferences).thenReturn(preferences)
        InventoryProductionFixtures.bank(bot, Item(1925, 800), Item(1929, 100))
        val script = GeneralActivityCoordinator.getScript(bot)
        assertInstanceOf(FillWaterBotScript::class.java, script)
        assertEquals(500, (script as FillWaterBotScript).target)
        assertEquals(1925, script.empty)
        InventoryProductionFixtures.bank(bot, Item(1929, 400))
        assertNull(GeneralActivityCoordinator.getScript(bot))
    }

    @Test fun availableEmptiesBoundDefaultStockAndNoSuppliesQueueNoActivity() {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.preferences).thenReturn(BotPreference(mutableMapOf(), mutableSetOf(), mutableMapOf(),
            mutableSetOf(), mutableMapOf()))
        assertNull(GeneralActivityCoordinator.getScript(bot))
        GeneralActivityCoordinator.accept(bot)
        verify(bot.scriptStack, never()).push(any())
        InventoryProductionFixtures.bank(bot, Item(1925, 20))
        assertEquals(20, (GeneralActivityCoordinator.getScript(bot) as FillWaterBotScript).target)
    }

    @Test fun ownedKeysSelectAChestDiscoveredFromLoadedWorldObjects() {
        val bot = InventoryProductionFixtures.bot()
        val chest = mock(GameObject::class.java)
        val position = Position(2918, 3450)
        `when`(chest.id).thenReturn(OpenCrystalChestBotScript.CHEST)
        `when`(chest.position).thenReturn(position)
        `when`(world.objects.iterator()).thenReturn(Iterators.unmodifiableIterator(listOf(chest).iterator()))
        InventoryProductionFixtures.bank(bot, Item(OpenCrystalChestBotScript.KEY))
        val script = GeneralActivityCoordinator.getScript(bot)
        assertInstanceOf(OpenCrystalChestBotScript::class.java, script)
        assertEquals(position, (script as OpenCrystalChestBotScript).chestPosition)
    }
}
