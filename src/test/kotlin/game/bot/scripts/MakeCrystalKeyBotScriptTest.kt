package game.bot.scripts

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import engine.bot.coordinator.CombatCoordinator
import game.bot.scripts.MakeCrystalKeyBotScript.Companion.CrystalKeyData
import game.content.crystalChest.MakeCrystalKeyActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class MakeCrystalKeyBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun selectionRequiresBothHalvesAndExcludesTraining() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(985, 100))
        val script = MakeCrystalKeyBotScript(bot, 10.minutes)
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        CombatCoordinator(false).accept(bot)
        verify(bot.scriptStack).push(isA(NpcCombatScript::class.java))
        clearInvocations(bot.scriptStack)
        InventoryProductionFixtures.bank(bot, Item(987, 3))
        assertEquals(listOf(Item(985, 3), Item(987, 3)), script.bankBatch())
        CombatCoordinator(false).accept(bot)
        verify(bot.scriptStack).push(isA(MakeCrystalKeyBotScript::class.java))
        clearInvocations(bot.scriptStack)
        CombatCoordinator(true).accept(bot)
        verify(bot.scriptStack).push(isA(NpcCombatScript::class.java))
        verify(bot.scriptStack, never()).push(isA(MakeCrystalKeyBotScript::class.java))
        assertNull(CraftingScriptFactory.getProductionScript(bot, 99, true))
        assertNull(CraftingScriptFactory.getProductionScript(bot, 99, false))
    }

    @Test fun existingActionConsumesBothHalvesWithoutExperience() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(985), Item(987))
        InventoryProductionFixtures.execute(bot, MakeCrystalKeyActionItem(bot, 1))
        assertTrue(bot.inventory.contains(989))
        assertFalse(bot.inventory.contains(985))
        assertFalse(bot.inventory.contains(987))
        verify(bot.crafting, never()).addExperience(anyDouble())
    }

    @Test fun batchesRespectCapacityAndAssemblyDoesNotRequireCraftingLevels() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(985, 100), Item(987, 100))
        `when`(bot.crafting.staticLevel).thenReturn(0)
        `when`(bot.crafting.level).thenReturn(0)
        val script = InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes))
        assertTrue(script.isEligible())
        assertEquals(listOf(Item(985, 14), Item(987, 14)), script.bankBatch())
        assertTrue(script.onInit(false))
    }

    @Test fun missingHalfRequestsBulkStockButOwnedInputsQualify() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(985), Item(987))
        assertTrue(InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes)).onInit(false))
        bot.inventory.remove(987)
        assertFalse(InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes)).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(987, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(985, 1_000)
    }

    @Test fun sharedRegistrationRestoresZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = CrystalKeyData().apply {
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(MakeCrystalKeyBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), data)
            assertInstanceOf(MakeCrystalKeyBotScript::class.java, restored)
            val snapshot = (restored as MakeCrystalKeyBotScript).snapshot()
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
        InventoryProductionFixtures.bank(bot, Item(985), Item(987))
        `when`(bot.health).thenReturn(0)
        assertFalse(InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes)).onInit(false))
        `when`(bot.health).thenReturn(99)
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        `when`(bot.isLocked).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes)).onInit(false))
    }

    @Test fun fullProcessableInventoryAvoidsRedundantBanking() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(985, 14), Item(987, 14))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes))
        assertFalse(script.onBankRequested(false))
    }

    @Test fun exhaustedBankingBudgetSurvivesSerialization() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(985), Item(987))
        val script = InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        val json = JsonObject()
        script.snapshot().save(json)
        val data = CrystalKeyData().apply { load(json) }
        assertEquals(3, data.bankFailures)
        assertFalse(InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, data)).onInit(true))
    }

    @Test fun failedInteractionsExhaustTheSavedBudget() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(985), Item(987))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(MakeCrystalKeyBotScript(bot, script.snapshot())).onInit(true))
    }
}
