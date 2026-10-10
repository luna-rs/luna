package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.obj.resource.fillable.WaterResource
import game.bot.scripts.skills.MakeSoftClayBotScript.Companion.SoftClayData
import game.skill.crafting.potteryCrafting.MakeSoftClayActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class MakeSoftClayBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun preparationRequiresBothInputsAndStaysOutOfTraining() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434, 100))
        val script = MakeSoftClayBotScript(bot, 1929, 10.minutes)
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        assertNull(CraftingScriptFactory.getProductionScript(bot, 99, false))
        InventoryProductionFixtures.bank(bot, Item(1929, 3))
        assertEquals(listOf(Item(434, 3), Item(1929, 3)), script.bankBatch())
        assertNull(CraftingScriptFactory.getProductionScript(bot, 99, true))
        assertInstanceOf(MakeSoftClayBotScript::class.java,
            CraftingScriptFactory.getProductionScript(bot, 1, false))
        assertThrows(IllegalArgumentException::class.java) { MakeSoftClayBotScript(bot, 1925, 10.minutes) }
    }

    @Test fun allSupportedWaterContainersReturnEmptiesAtFullCapacityWithoutExperience() {
        for ((empty, filled) in WaterResource.FILLABLES) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(434, 14), Item(filled, 14))
            assertTrue(bot.inventory.isFull)
            val script = CraftingScriptFactory.getProductionScript(bot, 1, false)
            assertInstanceOf(MakeSoftClayBotScript::class.java, script)
            assertEquals(filled, (script as MakeSoftClayBotScript).water)
            val action = MakeSoftClayActionItem(bot)
            bot.actions.submit(action)
            repeat(14) { assertFalse(action.run()) }
            assertTrue(action.run())
            assertEquals(14, bot.inventory.computeAmountForId(empty))
            assertEquals(14, bot.inventory.computeAmountForId(1761))
            assertFalse(bot.inventory.contains(434))
            assertFalse(bot.inventory.contains(filled))
            verify(bot.crafting, never()).addExperience(anyDouble())
        }
    }

    @Test fun batchesRespectCapacityAndPreparationDoesNotRequireCraftingLevels() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434, 100), Item(1929, 100))
        `when`(bot.crafting.staticLevel).thenReturn(0)
        `when`(bot.crafting.level).thenReturn(0)
        val script = InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes))
        assertTrue(script.isEligible())
        assertEquals(listOf(Item(434, 14), Item(1929, 14)), script.bankBatch())
        assertTrue(script.onInit(false))
    }

    @Test fun missingWaterRequestsBulkStockButOwnedInputsQualify() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(434), Item(1929))
        assertTrue(InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes)).onInit(false))
        bot.inventory.remove(1929)
        assertFalse(InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes)).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(1929, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(434, 1_000)
    }

    @Test fun sharedRegistrationRestoresZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = SoftClayData().apply {
                recipe = "1929"
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(MakeSoftClayBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data)
            assertInstanceOf(MakeSoftClayBotScript::class.java, restored)
            val snapshot = (restored as MakeSoftClayBotScript).snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(manager)
        }
    }

    @Test fun startupRejectsDeathCombatAndLocks() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434), Item(1929))
        `when`(bot.health).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes)).onInit(false))
        `when`(bot.health).thenReturn(99)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(bot.isLocked).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes)).onInit(false))
    }

    @Test fun fullProcessableInventoryAvoidsRedundantBanking() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(434, 14), Item(1929, 14))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes))
        assertFalse(script.onBankRequested(false))
    }

    @Test fun exhaustedBankingBudgetSurvivesSerialization() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434), Item(1929))
        val script = InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        val json = JsonObject()
        script.snapshot().save(json)
        val data = SoftClayData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, data)).onInit(true))
    }

    @Test fun failedInteractionsExhaustTheSavedBudget() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(434), Item(1929))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, script.snapshot())).onInit(true))
    }
}
