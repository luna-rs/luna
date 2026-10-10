package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CookingScriptFactory
import game.bot.scripts.FillWaterBotScript
import game.bot.scripts.skills.AssembleFoodBotScript.Companion.AssemblyData
import game.skill.cooking.cookFood.MakeWineActionItem
import game.skill.cooking.cookFood.MakeWineActionItem.Companion.wineFermentTask
import game.skill.cooking.prepareFood.IncompleteFood
import io.luna.game.action.ActionState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.task.TaskManager
import io.luna.game.task.TaskState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class WineAssemblyBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private val food = IncompleteFood.UNFERMENTED_WINE
    private fun bot(level: Int = 35): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.cooking.staticLevel).thenReturn(level)
        `when`(it.cooking.level).thenReturn(level)
    }

    @Test fun factorySelectsOwnedWineForTrainingAtLevel35AndSnapshotsRestoreIt() {
        val bot = bot(34)
        InventoryProductionFixtures.bank(bot, Item(1987, 100), Item(1937, 100))
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 34, training = true))
        `when`(bot.cooking.staticLevel).thenReturn(35)
        `when`(bot.cooking.level).thenReturn(35)
        for (training in listOf(false, true)) {
            val script = CookingScriptFactory.getAssemblyScript(bot, 35, training)!!
            assertEquals(food, script.food)
            assertEquals(listOf(Item(1987, 14), Item(1937, 14)), script.bankBatch())
            val json = JsonObject()
            script.snapshot().save(json)
            val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
            assertEquals(food, restored.food)
            assertEquals(1937, restored.secondary)
            assertEquals(script.bankBatch(), restored.bankBatch())
        }
        assertEquals(food, (CookingScriptFactory.getTrainingScript(bot, 35, mutableListOf())
            as AssembleFoodBotScript).food)
    }

    @Test fun emptyJugsQueueBoundedWaterPreparationOnlyWhenGrapesAreOwned() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1987, 30), Item(1935, 8))
        val script = InventoryProductionFixtures.active(AssembleFoodBotScript(bot, food, 10.minutes))
        assertFalse(script.isEligible())
        assertTrue(script.canPrepareWater())
        assertEquals(food, CookingScriptFactory.getAssemblyScript(bot, 35)!!.food)
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 35, training = true))
        assertFalse(script.onInit(false))
        val filling = ArgumentCaptor.forClass(FillWaterBotScript::class.java)
        verify(bot.scriptStack).softPushHead(filling.capture())
        assertEquals(1935, filling.value.empty)
        assertEquals(8, filling.value.target)
        verify(bot.preferences, never()).raiseWantedItemTarget(anyInt(), anyInt())
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(1935, 8))
        assertFalse(script.canPrepareWater())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 35))
    }

    @Test fun batchUsesLimitedWaterStockAndMissingInputsRequestBulkTargets() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1987, 100), Item(1937, 3))
        val script = AssembleFoodBotScript(bot, food, 10.minutes)
        assertEquals(listOf(Item(1987, 3), Item(1937, 3)), script.bankBatch())
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(1987, 100))
        assertFalse(InventoryProductionFixtures.active(script).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(1937, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(1987, 1_000)
    }

    @Test fun finishingStopsMixingButLeavesCreatedWineFermenting() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(1987, 2), Item(1937, 2))
        val action = MakeWineActionItem(bot, 2)
        bot.actions.submit(action)
        assertFalse(action.run())
        val fermentation = bot.wineFermentTask!!
        val manager = TaskManager()
        manager.schedule(fermentation)
        AssembleFoodBotScript(bot, food, 10.minutes).finish()
        assertEquals(ActionState.INTERRUPTED, action.state)
        assertEquals(TaskState.RUNNING, fermentation.state)
        assertEquals(1, bot.inventory.computeAmountForId(1987))
        assertEquals(1, bot.inventory.computeAmountForId(1937))
        repeat(20) { manager.runTaskIteration() }
        assertEquals(1, bot.inventory.computeAmountForId(1993))
        assertEquals(TaskState.CANCELLED, fermentation.state)
        verify(bot.cooking, times(1)).addExperience(food.exp)
    }

    @Test fun temporaryCookingDrainAndUnsafeStatesDoNotStartMixing() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(1987), Item(1937))
        `when`(bot.cooking.level).thenReturn(34)
        val drained = InventoryProductionFixtures.active(AssembleFoodBotScript(bot, food, 10.minutes))
        assertTrue(drained.executeInZone())
        assertTrue(drained.isTerminated())
        verify(bot.actionHandler.inventory, never()).useItem(1987, -1)
        for (state in 0..2) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            assertFalse(InventoryProductionFixtures.active(AssembleFoodBotScript(bot, food, 10.minutes)).onInit(false))
        }
        assertTrue(bot.inventory.contains(1987))
        assertTrue(bot.inventory.contains(1937))
        assertNull(bot.wineFermentTask)
    }
}
