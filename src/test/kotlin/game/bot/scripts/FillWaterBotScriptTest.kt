package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.FillWaterBotScript.Companion.FillWaterData
import game.bot.scripts.skills.MakeSoftClayBotScript
import game.obj.resource.fillable.FillActionItem
import game.obj.resource.fillable.WaterResource
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class FillWaterBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun existingActionFillsAllSupportedContainersWithoutExperience() {
        for ((empty, filled) in WaterResource.FILLABLES) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(empty))
            InventoryProductionFixtures.execute(bot, FillActionItem(bot, Item(empty), Item(filled), WaterResource, 1))
            assertTrue(bot.inventory.contains(filled))
            assertFalse(bot.inventory.contains(empty))
            verify(bot.crafting, never()).addExperience(anyDouble())
        }
    }

    @Test fun factoryFindsPrerequisiteAndStartupQueuesOnlyNeededRefill() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434, 100), Item(1925, 5))
        assertNull(CraftingScriptFactory.getProductionScript(bot, 99, true))
        val preparation = CraftingScriptFactory.getProductionScript(bot, 99, false)
        assertInstanceOf(MakeSoftClayBotScript::class.java, preparation)
        assertFalse(InventoryProductionFixtures.active(preparation!!).onInit(false))
        val queued = ArgumentCaptor.forClass(FillWaterBotScript::class.java)
        verify(bot.scriptStack).softPushHead(queued.capture())
        assertEquals(1925, queued.value.empty)
        assertEquals(5, queued.value.target)
        verify(bot.preferences, never()).raiseWantedItemTarget(1929, 1_000)
    }

    @Test fun filledWaterAvoidsRefillAndMissingClayCannotTriggerIt() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434), Item(1925), Item(1929))
        val script = InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes))
        assertFalse(script.canPrepareWater())
        assertTrue(script.onInit(false))
        verify(bot.scriptStack, never()).softPushHead(any())
        bot.bank.remove(434)
        bot.bank.remove(1929)
        assertFalse(script.canPrepareWater())
    }

    @Test fun exhaustedWaterAtTheNextBankVisitQueuesRefillWithoutBuyingFilledContainers() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434, 100), Item(1925, 14))
        val script = InventoryProductionFixtures.active(MakeSoftClayBotScript(bot, 1929, 10.minutes))
        script.onBankOpen(false)
        assertTrue(script.isTerminated())
        verify(bot.scriptStack).softPushHead(isA(FillWaterBotScript::class.java))
        verify(bot.preferences, never()).raiseWantedItemTarget(1929, 1_000)
    }

    @Test fun unsafeStartupCannotQueuePreparationEvenWithEnoughWater() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434, 14), Item(1929, 14))
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(FillWaterBotScript(bot, 1925, 14, 10.minutes)).onInit(false))
        verify(bot.scriptStack, never()).softPushHead(any())
    }

    @Test fun refillBatchUsesTotalFilledStockAndNeverExceedsItsTarget() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1925, 100), Item(1929, 4))
        InventoryProductionFixtures.inventory(bot, Item(1929, 2))
        val script = FillWaterBotScript(bot, 1925, 14, 10.minutes)
        assertEquals(listOf(Item(1925, 8)), script.bankBatch())
        assertThrows(IllegalArgumentException::class.java) { FillWaterBotScript(bot, 434, 14, 10.minutes) }
        assertEquals(listOf(Item(1925, 28)), FillWaterBotScript(bot, 1925, 1_000, 10.minutes).bankBatch())
        assertThrows(IllegalArgumentException::class.java) { FillWaterBotScript(bot, 1925, 0, 10.minutes) }
    }

    @Test fun verifiedFilledStockCompletesWithoutSelectingAConsumer() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(434, 14), Item(1929, 14))
        assertFalse(InventoryProductionFixtures.active(FillWaterBotScript(bot, 1925, 14, 10.minutes)).onInit(false))
        verify(bot.scriptStack, never()).softPushHead(any())
        clearInvocations(bot.scriptStack)
        bot.bank.remove(Item(1929, 14))
        val script = InventoryProductionFixtures.active(FillWaterBotScript(bot, 1925, 14, 10.minutes))
        assertFalse(script.onInit(false))
        verify(bot.scriptStack, never()).softPushHead(any())
    }

    @Test fun missingSourcesExhaustBudgetAndRestorationDoesNotGrantMoreAttempts() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1925))
        val script = InventoryProductionFixtures.active(FillWaterBotScript(bot, 1925, 14, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        val json = JsonObject()
        script.snapshot().save(json)
        val data = FillWaterData().apply { load(json) }
        assertEquals(3, data.failures)
        assertFalse(InventoryProductionFixtures.active(FillWaterBotScript(bot, data)).onInit(true))
        verify(bot.scriptStack, never()).softPushHead(any())
    }

    @Test fun sharedRegistrationRestoresContainerTargetZonesAndCounters() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = FillWaterData().apply {
                empty = 1925
                target = 14
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(FillWaterBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data) as FillWaterBotScript
            val snapshot = restored.snapshot()
            assertEquals(data.empty, snapshot.empty)
            assertEquals(data.target, snapshot.target)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(manager) }
    }
}
