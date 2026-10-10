package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CookingScriptFactory
import game.bot.scripts.FillWaterBotScript
import game.bot.scripts.skills.MakeDoughBotScript.Companion.DoughData
import game.obj.resource.fillable.WaterResource
import game.skill.cooking.prepareFood.IncompleteFood
import game.skill.cooking.prepareFood.PrepareFoodActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.task.Task
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class MakeDoughBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(level: Int = 99): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.cooking.staticLevel).thenReturn(level)
        `when`(it.cooking.level).thenReturn(level)
    }

    @Test fun allRecipesAndContainersPreserveOutputsWithoutExperience() {
        for (food in IncompleteFood.DOUGH.values) for ((empty, filled) in WaterResource.FILLABLES) {
            val bot = bot(food.lvl)
            InventoryProductionFixtures.inventory(bot, Item(1933, 9), Item(filled, 9))
            val script = MakeDoughBotScript(bot, food, filled, 10.minutes)
            assertTrue(script.isEligible())
            val action = PrepareFoodActionItem(bot, food, mutableSetOf(1933, filled), 9)
            bot.actions.submit(action)
            repeat(8) { assertFalse(action.run()) }
            assertTrue(action.run())
            assertEquals(9, bot.inventory.computeAmountForId(food.id))
            assertEquals(9, bot.inventory.computeAmountForId(1931))
            assertEquals(9, bot.inventory.computeAmountForId(empty))
            assertEquals(1, bot.inventory.computeRemainingSize())
            assertFalse(bot.inventory.contains(1933))
            assertFalse(bot.inventory.contains(filled))
            verify(bot.cooking, never()).addExperience(anyDouble())
        }
    }

    @Test fun factoryAndStartupRespectRecipeLevelsAndOwnedSupplies() = runBlocking<Unit> {
        val bot = bot(34)
        assertNull(CookingScriptFactory.getPreparationScript(bot, 34))
        InventoryProductionFixtures.bank(bot, Item(1933, 100), Item(1929, 100))
        val options = listOf(IncompleteFood.BREAD_DOUGH, IncompleteFood.PASTRY_DOUGH)
        assertTrue(CookingScriptFactory.getPreparationScript(bot, 34)!!.food in options)
        val high = InventoryProductionFixtures.active(
            MakeDoughBotScript(bot, IncompleteFood.PIZZA_BASE, 1929, 10.minutes))
        assertFalse(high.isEligible())
        assertFalse(high.onInit(false))
        assertThrows(IllegalArgumentException::class.java) {
            MakeDoughBotScript(bot, IncompleteFood.UNCOOKED_CAKE, 1929, 10.minutes)
        }
        assertThrows(IllegalArgumentException::class.java) {
            MakeDoughBotScript(bot, IncompleteFood.BREAD_DOUGH, 1925, 10.minutes)
        }
    }

    @Test fun batchSizingReservesReturnedPotsAndFullInputsRequestBanking() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1933, 100), Item(1929, 2))
        val script = InventoryProductionFixtures.active(
            MakeDoughBotScript(bot, IncompleteFood.BREAD_DOUGH, 1929, 10.minutes))
        assertEquals(listOf(Item(1933, 2), Item(1929, 2)), script.bankBatch())
        InventoryProductionFixtures.bank(bot, Item(1929, 98))
        assertEquals(listOf(Item(1933, 9), Item(1929, 9)), script.bankBatch())
        InventoryProductionFixtures.inventory(bot, Item(1933, 14), Item(1929, 14))
        assertTrue(script.onBankRequested(false))
        script.executeInZone()
        verify(bot.actionHandler.widgets, never()).clickCloseInterface()
    }

    @Test fun missingWaterQueuesReusablePreparationOnlyWithOwnedFlourAndEmpties() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1933, 50), Item(1925, 20))
        val script = InventoryProductionFixtures.active(
            MakeDoughBotScript(bot, IncompleteFood.BREAD_DOUGH, 1929, 10.minutes))
        assertTrue(script.canPrepareWater())
        assertNotNull(CookingScriptFactory.getPreparationScript(bot, 1))
        assertFalse(script.onInit(false))
        verify(bot.scriptStack).softPushHead(any(FillWaterBotScript::class.java))
        verify(bot.preferences, never()).raiseWantedItemTarget(1929, 1_000)
        bot.bank.remove(Item(1933, 50))
        assertFalse(MakeDoughBotScript(bot, IncompleteFood.BREAD_DOUGH, 1929, 10.minutes).canPrepareWater())
    }

    @Test fun missingConsumablesRequestBulkTargetsAndUnsafeBotsDoNotStart() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(1933))
        assertFalse(InventoryProductionFixtures.active(
            MakeDoughBotScript(bot, IncompleteFood.BREAD_DOUGH, 1929, 10.minutes)).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(1929, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(1933, 1_000)
        InventoryProductionFixtures.bank(bot, Item(1929))
        for (state in 0..2) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            assertFalse(InventoryProductionFixtures.active(
                MakeDoughBotScript(bot, IncompleteFood.BREAD_DOUGH, 1929, 10.minutes)).onInit(false))
        }
    }

    @Test fun failedInteractionAndBankingBudgetsSurviveSerialization() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(1933), Item(1929))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(
            MakeDoughBotScript(bot, IncompleteFood.BREAD_DOUGH, 1929, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        val json = JsonObject()
        script.snapshot().save(json)
        val data = DoughData().apply { load(json) }
        assertEquals(3, data.failures)
        assertFalse(InventoryProductionFixtures.active(MakeDoughBotScript(bot, data)).onInit(true))
        val banking = InventoryProductionFixtures.active(
            MakeDoughBotScript(bot, IncompleteFood.BREAD_DOUGH, 1929, 10.minutes))
        assertTrue(banking.onInit(false))
        repeat(3) { assertTrue(banking.onBankRequested(false)) }
        assertFalse(banking.onBankRequested(false))
        assertEquals(3, banking.snapshot().bankFailures)
        assertFalse(InventoryProductionFixtures.active(MakeDoughBotScript(bot, banking.snapshot())).onInit(true))
    }

    @Test fun normalInteractionSelectsTheConfiguredDialogueEntryAndConfirmsConsumption() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation ->
            scheduler.invoke(invocation.getArgument<Task>(0))
            null
        }.`when`(world).schedule(any(Task::class.java))
        try {
            for ((index, food) in IncompleteFood.DOUGH.values.withIndex()) {
                val bot = bot(food.lvl)
                InventoryProductionFixtures.inventory(bot, Item(1933, 9), Item(1929, 9))
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(1933).onItem(1929)).thenReturn(true)
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = PrepareFoodActionItem(bot, food, mutableSetOf(1933, 1929), 9)
                    bot.actions.submit(action)
                    repeat(9) { action.run() }
                    null
                }.`when`(widgets).clickMakeItem(index, 9)
                val script = InventoryProductionFixtures.active(MakeDoughBotScript(bot, food, 1929, 10.minutes))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                assertEquals(9, bot.inventory.computeAmountForId(food.id))
                assertEquals(0, script.snapshot().failures)
                verify(bot.actionHandler.inventory.useItem(1933)).onItem(1929)
                verify(bot.actionHandler.widgets).clickMakeItem(index, 9)
            }
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }
    @Test fun centralRegistrationRestoresRecipeWaterZonesAndRetryCounters() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = DoughData().apply {
                recipe = IncompleteFood.PITA_DOUGH.name
                water = 1937
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val script = registry.loadScript(MakeDoughBotScript::class.qualifiedName, bot(), data)
                as MakeDoughBotScript
            val snapshot = script.snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.water, snapshot.water)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}