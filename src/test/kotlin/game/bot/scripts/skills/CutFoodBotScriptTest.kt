package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CookingScriptFactory
import game.bot.scripts.skills.CutFoodBotScript.Companion.CutFoodData
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.cooking.prepareFood.PrepareFoodActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class CutFoodBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.cooking.staticLevel).thenReturn(1)
        `when`(it.cooking.level).thenReturn(1)
    }

    @Test fun realPreparationMakesTwentyFourRingsPreservesKnifeAndAwardsNoExperience() {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(2114, 6), Item(946))
        val action = PrepareFoodActionItem(bot, IncompleteFood.PINEAPPLE_RING, mutableSetOf(2114, 946), 6)
        bot.actions.submit(action)
        repeat(5) { assertFalse(action.run()) }
        assertTrue(action.run())
        assertEquals(24, bot.inventory.computeAmountForId(2118))
        assertEquals(1, bot.inventory.computeAmountForId(946))
        assertFalse(bot.inventory.contains(2114))
        assertEquals(3, bot.inventory.computeRemainingSize())
        verify(bot.cooking, never()).addExperience(anyDouble())
    }

    @Test fun ownedSuppliesQualifyForNonTrainingSelectionOnlyAtRequiredLevel() {
        val bot = bot()
        assertNull(CookingScriptFactory.getNonTrainingPreparation(bot, 1))
        InventoryProductionFixtures.inventory(bot, Item(2114), Item(946))
        assertTrue(CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes).isEligible())
        assertInstanceOf(CutFoodBotScript::class.java, CookingScriptFactory.getNonTrainingPreparation(bot, 1))
        assertNull(CookingScriptFactory.getNonTrainingPreparation(bot, 0))
        assertInstanceOf(CookFoodBotScript::class.java,
            CookingScriptFactory.getTrainingScript(bot, 1, mutableListOf()))
        `when`(bot.cooking.staticLevel).thenReturn(0)
        assertFalse(CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes).isEligible())
    }

    @Test fun bankBatchesReserveAllOutputSlotsAndLimitStock() {
        val bot = bot()
        val script = CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes)
        InventoryProductionFixtures.bank(bot, Item(2114, 100))
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(946, 20))
        assertEquals(listOf(Item(946), Item(2114, 6)), script.bankBatch())
        bot.bank.remove(Item(2114, 98))
        assertEquals(listOf(Item(946), Item(2114, 2)), script.bankBatch())
    }

    @Test fun insufficientOutputSpaceRequestsBankingBeforeInteracting() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(2114, 7), Item(946))
        val script = InventoryProductionFixtures.active(CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes))
        assertTrue(script.onBankRequested(false))
        script.executeInZone()
        verify(bot.actionHandler.widgets, never()).clickCloseInterface()
    }

    @Test fun missingSuppliesUseRealisticConsumableAndToolTargetsAndUnsafeBotsStop() = runBlocking<Unit> {
        val bot = bot()
        assertFalse(InventoryProductionFixtures.active(CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes)).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(2114, 1_000)
        verify(bot.preferences).raiseWantedItemTarget(946, 3)
        InventoryProductionFixtures.bank(bot, Item(2114), Item(946))
        for (state in 0..3) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            `when`(bot.cooking.level).thenReturn(if (state == 3) 0 else 1)
            assertFalse(InventoryProductionFixtures.active(CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes)).onInit(false))
        }
    }

    @Test fun normalInteractionRequestsSixCutsAndConfirmsActualInputConsumption() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation ->
            scheduler.invoke(invocation.getArgument<Task>(0))
            null
        }.`when`(world).schedule(any(Task::class.java))
        try {
            val bot = bot()
            InventoryProductionFixtures.inventory(bot, Item(2114, 6), Item(946))
            `when`(bot.personality.isDextrous).thenReturn(true)
            `when`(bot.overlays.player).thenReturn(bot)
            `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
            `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
            `when`(bot.actionHandler.inventory.useItem(2114).onItem(946)).thenReturn(true)
            val widgets = bot.actionHandler.widgets
            doAnswer {
                val action = PrepareFoodActionItem(bot, IncompleteFood.PINEAPPLE_RING, mutableSetOf(2114, 946), 6)
                bot.actions.submit(action)
                repeat(6) { action.run() }
                null
            }.`when`(widgets).clickMakeItem(0, 6)
            val script = InventoryProductionFixtures.active(CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes))
            assertTrue(withTimeout(5_000) { script.executeInZone() })
            assertEquals(24, bot.inventory.computeAmountForId(2118))
            assertEquals(0, script.snapshot().failures)
            verify(bot.actionHandler.inventory.useItem(2114)).onItem(946)
            verify(widgets).clickMakeItem(0, 6)
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun failedInteractionAndBankingBudgetsCannotBeResetByRestoring() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(2114), Item(946))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        val json = JsonObject()
        script.snapshot().save(json)
        val data = CutFoodData().apply { load(json) }
        assertEquals(3, data.failures)
        assertFalse(InventoryProductionFixtures.active(CutFoodBotScript(bot, data)).onInit(true))
        val banking = InventoryProductionFixtures.active(CutFoodBotScript(bot, IncompleteFood.PINEAPPLE_RING, 10.minutes))
        assertTrue(banking.onInit(false))
        repeat(3) { assertTrue(banking.onBankRequested(false)) }
        assertFalse(banking.onBankRequested(false))
        assertEquals(3, banking.snapshot().bankFailures)
        assertFalse(InventoryProductionFixtures.active(CutFoodBotScript(bot, banking.snapshot())).onInit(true))
    }

    @Test fun centralRegistrationRestoresDurationZonesAndRetryBudgets() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = CutFoodData().apply {
                recipe = IncompleteFood.PINEAPPLE_RING.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val restored = registry.loadScript(CutFoodBotScript::class.qualifiedName, bot(), data)
                as CutFoodBotScript
            val snapshot = restored.snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}