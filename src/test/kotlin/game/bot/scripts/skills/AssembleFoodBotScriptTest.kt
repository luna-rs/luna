package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CookingScriptFactory
import game.bot.scripts.skills.AssembleFoodBotScript.Companion.AssemblyData
import game.bot.scripts.FillWaterBotScript
import game.obj.resource.fillable.WaterResource
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

class AssembleFoodBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(level: Int = 35): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.cooking.staticLevel).thenReturn(level)
        `when`(it.cooking.level).thenReturn(level)
    }

    @Test fun everyRecipeAndAlternativeProcessesFullInventoryThroughRealGameplayWithoutExperience() {
        for (food in AssembleFoodBotScript.RECIPES) for (secondary in food.otherIngredients) {
            val bot = bot(food.lvl)
            InventoryProductionFixtures.inventory(bot, Item(food.baseIngredient, 14), Item(secondary, 14))
            assertTrue(bot.inventory.isFull)
            val action = PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, secondary), 14)
            bot.actions.submit(action)
            repeat(13) { assertFalse(action.run()) }
            assertTrue(action.run())
            assertEquals(14, bot.inventory.computeAmountForId(food.id))
            assertFalse(bot.inventory.contains(food.baseIngredient))
            assertFalse(bot.inventory.contains(secondary))
            WaterResource.FILLABLES.inverse()[secondary]?.let { empty ->
                assertEquals(14, bot.inventory.computeAmountForId(empty))
            }
            verify(bot.cooking, never()).addExperience(anyDouble())
        }
    }

    @Test fun selectionRequiresOwnedInputsAndLevel35AndRejectsUnvalidatedRecipes() = runBlocking<Unit> {
        val bot = bot(34)
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 34))
        InventoryProductionFixtures.bank(bot, Item(2283, 100), Item(1982, 100))
        val script = InventoryProductionFixtures.active(
            AssembleFoodBotScript(bot, IncompleteFood.INCOMPLETE_PIZZA, 10.minutes))
        assertFalse(script.isEligible())
        assertFalse(script.onInit(false))
        `when`(bot.cooking.staticLevel).thenReturn(35)
        `when`(bot.cooking.level).thenReturn(35)
        val chosen = CookingScriptFactory.getAssemblyScript(bot, 35)!!
        assertEquals(IncompleteFood.INCOMPLETE_PIZZA, chosen.food)
        assertInstanceOf(AssembleFoodBotScript::class.java, CookingScriptFactory.getNonTrainingPreparation(bot, 35))
        assertInstanceOf(CookFoodBotScript::class.java,
            CookingScriptFactory.getTrainingScript(bot, 35, mutableListOf()))
        assertThrows(IllegalArgumentException::class.java) {
            AssembleFoodBotScript(bot, IncompleteFood.UNCOOKED_CAKE, 10.minutes)
        }
    }

    @Test fun balancedBankBatchUsesSmallerStockAndFullPairsNeedNoPrematureBanking() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(2285, 100), Item(1985, 2))
        val script = InventoryProductionFixtures.active(
            AssembleFoodBotScript(bot, IncompleteFood.UNCOOKED_PLAIN_PIZZA, 10.minutes))
        assertEquals(listOf(Item(2285, 2), Item(1985, 2)), script.bankBatch())
        InventoryProductionFixtures.bank(bot, Item(1985, 98))
        assertEquals(listOf(Item(2285, 14), Item(1985, 14)), script.bankBatch())
        InventoryProductionFixtures.inventory(bot, Item(2285, 14), Item(1985, 14))
        assertFalse(script.onBankRequested(false))
    }

    @Test fun missingIngredientRequestsBulkStockAndUnsafeBotsDoNotStart() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(2283))
        assertFalse(InventoryProductionFixtures.active(
            AssembleFoodBotScript(bot, IncompleteFood.INCOMPLETE_PIZZA, 10.minutes)).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(1982, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(2283, 1_000)
        InventoryProductionFixtures.bank(bot, Item(1982))
        for (state in 0..2) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            assertFalse(InventoryProductionFixtures.active(
                AssembleFoodBotScript(bot, IncompleteFood.INCOMPLETE_PIZZA, 10.minutes)).onInit(false))
        }
    }

    @Test fun ordinaryInteractionSelectsOneRecipeAndConfirmsRealConsumption() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            val interactions = listOf(
                IncompleteFood.INCOMPLETE_PIZZA to 1982,
                IncompleteFood.UNCOOKED_PLAIN_PIZZA to 1985,
                IncompleteFood.UNCOOKED_MEAT_PIE to 2140,
                IncompleteFood.PART_MUD_PIE_2 to 1937)
            for ((food, secondary) in interactions) {
                val bot = bot(food.lvl)
                InventoryProductionFixtures.inventory(bot, Item(food.baseIngredient, 14), Item(secondary, 14))
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(food.baseIngredient).onItem(secondary)).thenReturn(true)
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, secondary), 14)
                    bot.actions.submit(action)
                    repeat(14) { action.run() }
                    null
                }.`when`(widgets).clickMakeItem(0, 14)
                val script = InventoryProductionFixtures.active(
                    AssembleFoodBotScript(bot, food, 10.minutes, secondary = secondary))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                assertEquals(14, bot.inventory.computeAmountForId(food.id))
                assertEquals(0, script.snapshot().failures)
                verify(widgets).clickMakeItem(0, 14)
            }
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    @Test fun interactionAndBankingRetryBudgetsSurviveRestoration() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(2283), Item(1982))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(
            AssembleFoodBotScript(bot, IncompleteFood.INCOMPLETE_PIZZA, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        val json = JsonObject()
        script.snapshot().save(json)
        val data = AssemblyData().apply { load(json) }
        assertEquals(3, data.failures)
        assertFalse(InventoryProductionFixtures.active(AssembleFoodBotScript(bot, data)).onInit(true))
        val banking = InventoryProductionFixtures.active(
            AssembleFoodBotScript(bot, IncompleteFood.INCOMPLETE_PIZZA, 10.minutes))
        assertTrue(banking.onInit(false))
        repeat(3) { assertTrue(banking.onBankRequested(false)) }
        assertFalse(banking.onBankRequested(false))
        assertEquals(3, banking.snapshot().bankFailures)
        assertFalse(InventoryProductionFixtures.active(AssembleFoodBotScript(bot, banking.snapshot())).onInit(true))
    }

    @Test fun everyPieRecipeEnforcesItsLevelAndSelectsOwnedAlternateInputs() {
        for (food in AssembleFoodBotScript.RECIPES.filter { it.name.contains("PIE") }) {
            for (secondary in food.otherIngredients) {
                val bot = bot(food.lvl - 1)
                InventoryProductionFixtures.bank(bot, Item(food.baseIngredient, 2), Item(secondary, 2))
                val script = AssembleFoodBotScript(bot, food, 10.minutes, secondary = secondary)
                assertFalse(script.isEligible(), food.name)
                `when`(bot.cooking.staticLevel).thenReturn(food.lvl)
                `when`(bot.cooking.level).thenReturn(food.lvl)
                assertTrue(script.isEligible(), food.name)
                val chosen = CookingScriptFactory.getAssemblyScript(bot, food.lvl)!!
                assertEquals(food, chosen.food)
                assertEquals(secondary, chosen.secondary)
                assertEquals(listOf(Item(food.baseIngredient, 2), Item(secondary, 2)), chosen.bankBatch())
            }
        }
    }

    @Test fun mudPieCanRequestEveryWaterContainerWithoutDemandingPurchasedWater() = runBlocking<Unit> {
        for ((empty, filled) in WaterResource.FILLABLES) {
            val bot = bot(29)
            InventoryProductionFixtures.bank(bot, Item(7164, 30), Item(empty, 10))
            val script = InventoryProductionFixtures.active(
                AssembleFoodBotScript(bot, IncompleteFood.PART_MUD_PIE_2, 10.minutes, secondary = filled))
            assertTrue(script.canPrepareWater())
            val chosen = CookingScriptFactory.getAssemblyScript(bot, 29)!!
            assertEquals(IncompleteFood.PART_MUD_PIE_2, chosen.food)
            assertEquals(filled, chosen.secondary)
            assertFalse(script.onInit(false))
            verify(bot.scriptStack).softPushHead(any(FillWaterBotScript::class.java))
            verify(bot.preferences, never()).raiseWantedItemTarget(filled, 1_000)
        }
    }

    @Test fun alternateIngredientSurvivesJsonAndLegacyPizzaStateHasItsOriginalIngredient() {
        val bot = bot(20)
        val script = AssembleFoodBotScript(bot, IncompleteFood.UNCOOKED_MEAT_PIE, 10.minutes, secondary = 2140)
        val json = JsonObject()
        script.snapshot().save(json)
        val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
        assertEquals(2140, restored.secondary)
        json.remove("secondary")
        val legacy = AssemblyData().apply { load(json) }
        assertEquals(IncompleteFood.UNCOOKED_MEAT_PIE.otherIngredients.first(), legacy.secondary)
        assertThrows(IllegalArgumentException::class.java) {
            AssembleFoodBotScript(bot, IncompleteFood.UNCOOKED_MEAT_PIE, 10.minutes, secondary = 1982)
        }
    }
    @Test fun centralRegistrationRestoresRecipeZonesDurationAndRetryState() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = AssemblyData().apply {
                recipe = IncompleteFood.PART_MUD_PIE_2.name
                secondary = 1937
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val script = registry.loadScript(AssembleFoodBotScript::class.qualifiedName, bot(), data)
                as AssembleFoodBotScript
            val snapshot = script.snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.secondary, snapshot.secondary)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }
}