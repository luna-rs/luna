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
import org.mockito.ArgumentCaptor
import kotlin.time.Duration.Companion.minutes

class AssembleFoodBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(level: Int = 35): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.cooking.staticLevel).thenReturn(level)
        `when`(it.cooking.level).thenReturn(level)
    }

    @Test fun everyRecipeAndAlternativeProcessesFullInventoryThroughRealGameplayWithRecipeExperience() {
        for (food in AssembleFoodBotScript.RECIPES) for (secondary in food.otherIngredients) {
            val bot = bot(food.lvl)
            val secondaryAmount = if (food == IncompleteFood.UNCOOKED_CURRY && secondary == 5970) 3 else 1
            val batches = 28 / (1 + secondaryAmount)
            InventoryProductionFixtures.inventory(bot, Item(food.baseIngredient, batches), Item(secondary, batches * secondaryAmount))
            assertTrue(bot.inventory.isFull)
            val action = PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, secondary), batches)
            bot.actions.submit(action)
            repeat(batches - 1) { assertFalse(action.run()) }
            assertTrue(action.run())
            assertEquals(batches, bot.inventory.computeAmountForId(food.id))
            assertFalse(bot.inventory.contains(food.baseIngredient))
            assertFalse(bot.inventory.contains(secondary))
            WaterResource.FILLABLES.inverse()[secondary]?.let { empty ->
                assertEquals(batches, bot.inventory.computeAmountForId(empty))
            }
            if (secondary == 1927) assertEquals(batches, bot.inventory.computeAmountForId(1925))
            if (food == IncompleteFood.CUP_OF_NETTLE_TEA) {
                assertEquals(batches, bot.inventory.computeAmountForId(1923))
                assertTrue(bot.inventory.isFull)
            }
            if (food.exp > 0.0) verify(bot.cooking, times(batches)).addExperience(food.exp)
            else verify(bot.cooking, never()).addExperience(anyDouble())
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
                IncompleteFood.MEAT_PIZZA to 2142,
                IncompleteFood.ANCHOVY_PIZZA to 319,
                IncompleteFood.PINEAPPLE_PIZZA to 2118,
                IncompleteFood.CHOCOLATE_CAKE to 1973,
                IncompleteFood.CHOCOLATE_CAKE to 1975,
                IncompleteFood.MILKY_NETTLE_TEA to 1927,
                IncompleteFood.NETTLE_WATER to 4241,
                IncompleteFood.CUP_OF_NETTLE_TEA to 4239,
                IncompleteFood.CUP_OF_MILKY_NETTLE_TEA to 1927,
                IncompleteFood.INCOMPLETE_STEW_WITH_POTATO to 1942,
                IncompleteFood.INCOMPLETE_STEW_WITH_MEAT to 2140,
                IncompleteFood.INCOMPLETE_STEW_WITH_MEAT to 2142,
                IncompleteFood.UNCOOKED_STEW_FROM_MEAT to 1942,
                IncompleteFood.UNCOOKED_STEW_FROM_POTATO to 2140,
                IncompleteFood.UNCOOKED_STEW_FROM_POTATO to 2142,
                IncompleteFood.UNCOOKED_CURRY to 2007,
                IncompleteFood.UNCOOKED_CURRY to 5970,
                IncompleteFood.UNCOOKED_MEAT_PIE to 2140,
                IncompleteFood.PART_MUD_PIE_2 to 1937)
            for ((food, secondary) in interactions) {
                val bot = bot(food.lvl)
                val secondaryAmount = if (food == IncompleteFood.UNCOOKED_CURRY && secondary == 5970) 3 else 1
                val batches = 28 / (1 + secondaryAmount)
                InventoryProductionFixtures.inventory(bot, Item(food.baseIngredient, batches), Item(secondary, batches * secondaryAmount))
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(food.baseIngredient).onItem(secondary)).thenReturn(true)
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, secondary), batches)
                    bot.actions.submit(action)
                    repeat(batches) { action.run() }
                    null
                }.`when`(widgets).clickMakeItem(0, batches)
                val script = InventoryProductionFixtures.active(
                    AssembleFoodBotScript(bot, food, 10.minutes, secondary = secondary))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                assertEquals(batches, bot.inventory.computeAmountForId(food.id))
                assertEquals(0, script.snapshot().failures)
                verify(widgets).clickMakeItem(0, batches)
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

    @Test fun pizzaToppingsSelectOwnedAlternativesAtTheirRequiredLevelsForTrainingAndProfit() {
        for (food in listOf(IncompleteFood.MEAT_PIZZA, IncompleteFood.ANCHOVY_PIZZA,
                            IncompleteFood.PINEAPPLE_PIZZA)) for (secondary in food.otherIngredients) {
            val bot = bot(food.lvl - 1)
            InventoryProductionFixtures.bank(bot, Item(2289, 50), Item(secondary, 3))
            assertNull(CookingScriptFactory.getAssemblyScript(bot, food.lvl - 1, training = true))
            `when`(bot.cooking.staticLevel).thenReturn(food.lvl)
            `when`(bot.cooking.level).thenReturn(food.lvl)
            for (training in listOf(false, true)) {
                val script = CookingScriptFactory.getAssemblyScript(bot, food.lvl, training)!!
                assertEquals(food, script.food)
                assertEquals(secondary, script.secondary)
                assertEquals(listOf(Item(2289, 3), Item(secondary, 3)), script.bankBatch())
            }
            val training = CookingScriptFactory.getTrainingScript(bot, food.lvl, mutableListOf())
                as AssembleFoodBotScript
            assertEquals(food, training.food)
            assertEquals(secondary, training.secondary)
            val json = JsonObject()
            training.snapshot().save(json)
            val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
            assertEquals(food, restored.food)
            assertEquals(secondary, restored.secondary)
        }
    }

    @Test fun trainingExcludesZeroExperiencePreparationAndNeedsBothPizzaInputs() {
        val bot = bot(65)
        InventoryProductionFixtures.bank(bot, Item(2289, 100), Item(2283, 100), Item(1982, 100))
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 65, training = true))
        assertInstanceOf(CookFoodBotScript::class.java,
            CookingScriptFactory.getTrainingScript(bot, 65, mutableListOf()))
        InventoryProductionFixtures.bank(bot, Item(2118, 100))
        assertEquals(IncompleteFood.PINEAPPLE_PIZZA,
            CookingScriptFactory.getAssemblyScript(bot, 65, training = true)!!.food)
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(2118, 100))
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 65, training = true))
    }

    @Test fun toppingDefinitionsMatchLunaItemsAndExperience() {
        assertEquals("Plain pizza", itemName(IncompleteFood.MEAT_PIZZA.baseIngredient))
        assertEquals("Meat pizza", itemName(IncompleteFood.MEAT_PIZZA.id))
        assertEquals("Anchovy pizza", itemName(IncompleteFood.ANCHOVY_PIZZA.id))
        assertEquals("Pineapple pizza", itemName(IncompleteFood.PINEAPPLE_PIZZA.id))
        assertEquals(listOf(26.0, 39.0, 45.0), listOf(IncompleteFood.MEAT_PIZZA.exp,
            IncompleteFood.ANCHOVY_PIZZA.exp, IncompleteFood.PINEAPPLE_PIZZA.exp))
    }

    @Test fun chocolateCakeUsesEitherOwnedChocolateInputAndRestoresItForTrainingAndProfit() {
        val food = IncompleteFood.CHOCOLATE_CAKE
        assertEquals(50, food.lvl)
        assertEquals(30.0, food.exp)
        assertEquals("Cake", itemName(food.baseIngredient))
        assertEquals("Chocolate cake", itemName(food.id))
        assertEquals(listOf("Chocolate bar", "Chocolate dust"), food.otherIngredients.map { itemName(it) })
        for (secondary in food.otherIngredients) {
            val bot = bot(49)
            InventoryProductionFixtures.bank(bot, Item(food.baseIngredient, 50), Item(secondary, 3))
            assertNull(CookingScriptFactory.getAssemblyScript(bot, 49))
            assertNull(CookingScriptFactory.getAssemblyScript(bot, 49, training = true))
            val script = AssembleFoodBotScript(bot, food, 10.minutes, secondary = secondary)
            assertFalse(script.isEligible())
            `when`(bot.cooking.staticLevel).thenReturn(50)
            `when`(bot.cooking.level).thenReturn(50)
            assertTrue(script.isEligible())
            for (training in listOf(false, true)) {
                val chosen = CookingScriptFactory.getAssemblyScript(bot, 50, training)!!
                assertEquals(food, chosen.food)
                assertEquals(secondary, chosen.secondary)
                assertEquals(listOf(Item(food.baseIngredient, 3), Item(secondary, 3)), chosen.bankBatch())
            }
            assertEquals(food, (CookingScriptFactory.getTrainingScript(bot, 50, mutableListOf())
                as AssembleFoodBotScript).food)
            val json = JsonObject()
            script.snapshot().save(json)
            val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
            assertEquals(food, restored.food)
            assertEquals(secondary, restored.secondary)
            bot.bank.clear()
            InventoryProductionFixtures.bank(bot, Item(secondary, 10))
            assertFalse(script.isEligible())
            assertNull(CookingScriptFactory.getAssemblyScript(bot, 50, training = true))
        }
    }

    @Test fun chocolateCakeActionRejectsInsufficientLevelWithoutConsumingOrAwardingExperience() {
        val food = IncompleteFood.CHOCOLATE_CAKE
        for (secondary in food.otherIngredients) {
            val bot = bot(49)
            InventoryProductionFixtures.inventory(bot, Item(food.baseIngredient), Item(secondary))
            val action = PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, secondary), 1)
            bot.actions.submit(action)
            assertTrue(action.run())
            assertTrue(bot.inventory.contains(food.baseIngredient))
            assertTrue(bot.inventory.contains(secondary))
            assertFalse(bot.inventory.contains(food.id))
            verify(bot.cooking, never()).addExperience(anyDouble())
        }
    }

    @Test fun milkyNettleTeaIsNonTrainingOnlyRequiresBothInputsAndPreservesSelectedRecipe() {
        val food = IncompleteFood.MILKY_NETTLE_TEA
        assertEquals(20, food.lvl)
        assertEquals(0.0, food.exp)
        assertEquals("Nettle tea", itemName(food.baseIngredient))
        assertEquals("Bucket of milk", itemName(food.otherIngredients.single()))
        assertEquals("Nettle tea", itemName(food.id))
        val bot = bot(19)
        InventoryProductionFixtures.bank(bot, Item(food.baseIngredient, 30), Item(1927, 2))
        val script = AssembleFoodBotScript(bot, food, 10.minutes)
        assertFalse(script.isEligible())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 19))
        `when`(bot.cooking.staticLevel).thenReturn(20)
        `when`(bot.cooking.level).thenReturn(20)
        assertTrue(script.isEligible())
        val selected = CookingScriptFactory.getAssemblyScript(bot, 20)!!
        assertEquals(food, selected.food)
        assertEquals(listOf(Item(food.baseIngredient, 2), Item(1927, 2)), selected.bankBatch())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 20, training = true))
        assertInstanceOf(CookFoodBotScript::class.java,
            CookingScriptFactory.getTrainingScript(bot, 20, mutableListOf()))
        assertInstanceOf(AssembleFoodBotScript::class.java,
            CookingScriptFactory.getNonTrainingPreparation(bot, 20))
        val json = JsonObject()
        selected.snapshot().save(json)
        val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
        assertEquals(food, restored.food)
        assertEquals(1927, restored.secondary)
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(food.baseIngredient, 30))
        assertFalse(script.isEligible())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 20))
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(1927, 30))
        assertFalse(script.isEligible())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 20))
    }

    @Test fun milkyNettleTeaPreservesExistingEmptyBucketsAndRejectsLowLevelsWithoutConsumption() {
        val food = IncompleteFood.MILKY_NETTLE_TEA
        val bot = bot(19)
        InventoryProductionFixtures.inventory(bot, Item(food.baseIngredient), Item(1927), Item(1925, 2))
        val rejected = PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, 1927), 1)
        bot.actions.submit(rejected)
        assertTrue(rejected.run())
        assertTrue(bot.inventory.contains(food.baseIngredient))
        assertTrue(bot.inventory.contains(1927))
        assertFalse(bot.inventory.contains(food.id))
        assertEquals(2, bot.inventory.computeAmountForId(1925))
        `when`(bot.cooking.level).thenReturn(20)
        InventoryProductionFixtures.execute(bot,
            PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, 1927), 1))
        assertFalse(bot.inventory.contains(food.baseIngredient))
        assertFalse(bot.inventory.contains(1927))
        assertEquals(1, bot.inventory.computeAmountForId(food.id))
        assertEquals(3, bot.inventory.computeAmountForId(1925))
        verify(bot.cooking, never()).addExperience(anyDouble())
    }

    @Test fun stewAndCurrySelectRegisteredOwnedRecipesAtTheirLevelsWithoutTraining() {
        for (food in listOf(IncompleteFood.UNCOOKED_STEW_FROM_MEAT, IncompleteFood.UNCOOKED_CURRY)) {
            assertEquals(food, IncompleteFood.ALL[food.id])
            assertEquals(0.0, food.exp)
            for (secondary in food.otherIngredients) {
                val bot = bot(food.lvl - 1)
                InventoryProductionFixtures.bank(bot, Item(food.baseIngredient, 100), Item(secondary, 100))
                assertNull(CookingScriptFactory.getAssemblyScript(bot, food.lvl - 1))
                `when`(bot.cooking.staticLevel).thenReturn(food.lvl)
                `when`(bot.cooking.level).thenReturn(food.lvl)
                val selected = CookingScriptFactory.getAssemblyScript(bot, food.lvl)!!
                assertEquals(food, selected.food)
                assertEquals(secondary, selected.secondary)
                assertNull(CookingScriptFactory.getAssemblyScript(bot, food.lvl, training = true))
                val json = JsonObject()
                selected.snapshot().save(json)
                val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
                assertEquals(food, restored.food)
                assertEquals(secondary, restored.secondary)
                assertEquals(selected.secondaryAmount, restored.secondaryAmount)
            }
        }
        assertTrue(IncompleteFood.UNCOOKED_STEW_FROM_POTATO in AssembleFoodBotScript.RECIPES)
        assertEquals("Incomplete stew", itemName(IncompleteFood.UNCOOKED_STEW_FROM_MEAT.baseIngredient))
        assertEquals("Uncooked stew", itemName(IncompleteFood.UNCOOKED_STEW_FROM_MEAT.id))
        assertEquals("Uncooked curry", itemName(IncompleteFood.UNCOOKED_CURRY.id))
    }

    @Test fun curryLeavesRespectThreePerOperationCapacityPartialStockAndMissingSupplyTargets() = runBlocking<Unit> {
        val bot = bot(60)
        val script = AssembleFoodBotScript(bot, IncompleteFood.UNCOOKED_CURRY, 10.minutes, secondary = 5970)
        InventoryProductionFixtures.bank(bot, Item(2001, 100), Item(5970, 2))
        assertFalse(script.isEligible())
        assertEquals(emptyList<Item>(), script.bankBatch())
        assertFalse(InventoryProductionFixtures.active(script).onInit(false))
        verify(bot.preferences).raiseWantedItemTarget(5970, 1_000)
        verify(bot.preferences, never()).raiseWantedItemTarget(2001, 1_000)
        InventoryProductionFixtures.bank(bot, Item(5970))
        assertTrue(script.isEligible())
        assertEquals(listOf(Item(2001), Item(5970, 3)), script.bankBatch())
        InventoryProductionFixtures.bank(bot, Item(5970, 17))
        assertEquals(listOf(Item(2001, 6), Item(5970, 18)), script.bankBatch())
        InventoryProductionFixtures.bank(bot, Item(5970, 100))
        assertEquals(listOf(Item(2001, 7), Item(5970, 21)), script.bankBatch())
        val spice = AssembleFoodBotScript(bot, IncompleteFood.UNCOOKED_CURRY, 10.minutes, secondary = 2007)
        InventoryProductionFixtures.bank(bot, Item(2007, 100))
        assertEquals(1, spice.secondaryAmount)
        assertEquals(listOf(Item(2001, 14), Item(2007, 14)), spice.bankBatch())
    }

    @Test fun curryDoesNotConsumeAnIncompleteLeafSetOrAwardExperienceBelowRequiredLevel() {
        val food = IncompleteFood.UNCOOKED_CURRY
        val bot = bot(60)
        InventoryProductionFixtures.inventory(bot, Item(2001), Item(5970, 2))
        InventoryProductionFixtures.execute(bot, PrepareFoodActionItem(bot, food, mutableSetOf(2001, 5970), 1))
        assertEquals(1, bot.inventory.computeAmountForId(2001))
        assertEquals(2, bot.inventory.computeAmountForId(5970))
        assertFalse(bot.inventory.contains(2009))
        InventoryProductionFixtures.inventory(bot, Item(5970))
        `when`(bot.cooking.level).thenReturn(59)
        InventoryProductionFixtures.execute(bot, PrepareFoodActionItem(bot, food, mutableSetOf(2001, 5970), 1))
        assertEquals(1, bot.inventory.computeAmountForId(2001))
        assertEquals(3, bot.inventory.computeAmountForId(5970))
        assertFalse(bot.inventory.contains(2009))
        verify(bot.cooking, never()).addExperience(anyDouble())
    }

    @Test fun potatoBasedStewSelectsEitherOwnedMeatAndRestoresItsExactRecipe() {
        val food = IncompleteFood.UNCOOKED_STEW_FROM_POTATO
        for (secondary in food.otherIngredients) {
            val bot = bot(24)
            InventoryProductionFixtures.bank(bot, Item(1997, 100), Item(secondary, 2))
            assertNull(CookingScriptFactory.getAssemblyScript(bot, 24))
            `when`(bot.cooking.staticLevel).thenReturn(25)
            val selected = CookingScriptFactory.getAssemblyScript(bot, 25)!!
            assertEquals(food, selected.food)
            assertEquals(secondary, selected.secondary)
            assertEquals(listOf(Item(1997, 2), Item(secondary, 2)), selected.bankBatch())
            assertNull(CookingScriptFactory.getAssemblyScript(bot, 25, training = true))
            val json = JsonObject()
            selected.snapshot().save(json)
            val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
            assertEquals(food, restored.food)
            assertEquals(secondary, restored.secondary)
        }
    }

    @Test fun initialBowlRecipesSelectOwnedSuppliesAndTrainingOnlyWhenTheyAwardExperience() {
        for (food in listOf(IncompleteFood.NETTLE_WATER, IncompleteFood.INCOMPLETE_STEW_WITH_POTATO,
                            IncompleteFood.INCOMPLETE_STEW_WITH_MEAT)) for (secondary in food.otherIngredients) {
            val bot = bot(food.lvl - 1)
            InventoryProductionFixtures.bank(bot, Item(1921, 100), Item(secondary, 2))
            assertNull(CookingScriptFactory.getAssemblyScript(bot, food.lvl - 1))
            `when`(bot.cooking.staticLevel).thenReturn(food.lvl)
            val selected = CookingScriptFactory.getAssemblyScript(bot, food.lvl)!!
            assertEquals(food, selected.food)
            assertEquals(secondary, selected.secondary)
            assertEquals(listOf(Item(1921, 2), Item(secondary, 2)), selected.bankBatch())
            val training = CookingScriptFactory.getAssemblyScript(bot, food.lvl, training = true)
            if (food.exp > 0.0) assertEquals(food, training!!.food) else assertNull(training)
            val json = JsonObject()
            selected.snapshot().save(json)
            val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
            assertEquals(food, restored.food)
            assertEquals(secondary, restored.secondary)
        }
    }

    @Test fun missingWaterBaseQueuesOnlyOwnedBowlsNeededForTheSelectedIngredient() = runBlocking<Unit> {
        for (food in listOf(IncompleteFood.NETTLE_WATER, IncompleteFood.INCOMPLETE_STEW_WITH_POTATO,
                            IncompleteFood.INCOMPLETE_STEW_WITH_MEAT)) for (secondary in food.otherIngredients) {
            val bot = bot(food.lvl)
            InventoryProductionFixtures.bank(bot, Item(1923, 30), Item(secondary, 5))
            val selected = CookingScriptFactory.getAssemblyScript(bot, food.lvl)!!
            assertEquals(food, selected.food)
            assertTrue(selected.canPrepareWater())
            assertNull(CookingScriptFactory.getAssemblyScript(bot, food.lvl, training = true))
            assertFalse(InventoryProductionFixtures.active(selected).onInit(false))
            val queued = ArgumentCaptor.forClass(FillWaterBotScript::class.java)
            verify(bot.scriptStack).softPushHead(queued.capture())
            assertEquals(1923, queued.value.empty)
            assertEquals(5, queued.value.target)
            verify(bot.preferences, never()).raiseWantedItemTarget(1921, 1_000)
            bot.bank.remove(Item(secondary, 5))
            assertFalse(AssembleFoodBotScript(bot, food, 10.minutes, secondary = secondary).canPrepareWater())
        }
    }

    @Test fun teaPouringRequiresLevel20AndOwnedCupsAndTeaForTrainingAndProfit() {
        val food = IncompleteFood.CUP_OF_NETTLE_TEA
        assertEquals(20, food.lvl)
        assertEquals(52.0, food.exp)
        assertEquals("Empty cup", itemName(food.baseIngredient))
        assertEquals("Nettle tea", itemName(food.otherIngredients.single()))
        assertEquals("Cup of tea", itemName(food.id))
        val bot = bot(19)
        InventoryProductionFixtures.bank(bot, Item(1980, 100), Item(4239, 2))
        val script = AssembleFoodBotScript(bot, food, 10.minutes)
        assertFalse(script.isEligible())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 19))
        `when`(bot.cooking.staticLevel).thenReturn(20)
        `when`(bot.cooking.level).thenReturn(20)
        for (training in listOf(false, true)) {
            val selected = CookingScriptFactory.getAssemblyScript(bot, 20, training)!!
            assertEquals(food, selected.food)
            assertEquals(listOf(Item(1980, 2), Item(4239, 2)), selected.bankBatch())
        }
        assertEquals(food, (CookingScriptFactory.getTrainingScript(bot, 20, mutableListOf())
            as AssembleFoodBotScript).food)
        assertFalse(script.canPrepareWater())
        val json = JsonObject()
        script.snapshot().save(json)
        val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
        assertEquals(food, restored.food)
        assertEquals(4239, restored.secondary)
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(1980, 100))
        assertFalse(script.isEligible())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 20))
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(4239, 100))
        assertFalse(script.isEligible())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 20))
    }

    @Test fun teaPouringBelowLevel20PreservesBothInputsAndAwardsNoExperience() {
        val bot = bot(19)
        val food = IncompleteFood.CUP_OF_NETTLE_TEA
        InventoryProductionFixtures.inventory(bot, Item(1980), Item(4239))
        InventoryProductionFixtures.execute(bot,
            PrepareFoodActionItem(bot, food, mutableSetOf(1980, 4239), 1))
        assertTrue(bot.inventory.contains(1980))
        assertTrue(bot.inventory.contains(4239))
        assertFalse(bot.inventory.contains(4242))
        assertFalse(bot.inventory.contains(1923))
        verify(bot.cooking, never()).addExperience(anyDouble())
    }

    @Test fun milkyTeaCupsRequireExistingTeaAndAreSelectedOnlyForNonTrainingAtLevel20() {
        val food = IncompleteFood.CUP_OF_MILKY_NETTLE_TEA
        assertEquals(4242, food.baseIngredient)
        assertEquals(20, food.lvl)
        assertEquals(0.0, food.exp)
        assertEquals("Cup of tea", itemName(food.baseIngredient))
        assertEquals("Cup of tea", itemName(food.id))
        val bot = bot(19)
        InventoryProductionFixtures.bank(bot, Item(4242, 100), Item(1927, 2))
        val script = AssembleFoodBotScript(bot, food, 10.minutes)
        assertFalse(script.isEligible())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 19))
        `when`(bot.cooking.staticLevel).thenReturn(20)
        val selected = CookingScriptFactory.getAssemblyScript(bot, 20)!!
        assertEquals(food, selected.food)
        assertEquals(listOf(Item(4242, 2), Item(1927, 2)), selected.bankBatch())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 20, training = true))
        val json = JsonObject()
        selected.snapshot().save(json)
        val restored = AssembleFoodBotScript(bot, AssemblyData().apply { load(json) })
        assertEquals(food, restored.food)
        assertEquals(1927, restored.secondary)
        bot.bank.clear()
        InventoryProductionFixtures.bank(bot, Item(1980, 100), Item(1927, 100))
        assertFalse(script.isEligible())
        assertNull(CookingScriptFactory.getAssemblyScript(bot, 20))
        assertFalse(script.canPrepareWater())
    }

    @Test fun milkyTeaCupsRejectEmptyCupsAndLowLevelsWithoutConsumptionOrExperience() {
        val food = IncompleteFood.CUP_OF_MILKY_NETTLE_TEA
        val bot = bot(20)
        InventoryProductionFixtures.inventory(bot, Item(1980), Item(1927))
        InventoryProductionFixtures.execute(bot,
            PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, 1927), 1))
        assertTrue(bot.inventory.contains(1980))
        assertTrue(bot.inventory.contains(1927))
        assertFalse(bot.inventory.contains(4243))
        assertFalse(bot.inventory.contains(1925))
        InventoryProductionFixtures.inventory(bot, Item(4242))
        `when`(bot.cooking.level).thenReturn(19)
        InventoryProductionFixtures.execute(bot,
            PrepareFoodActionItem(bot, food, mutableSetOf(food.baseIngredient, 1927), 1))
        assertTrue(bot.inventory.contains(4242))
        assertTrue(bot.inventory.contains(1927))
        assertFalse(bot.inventory.contains(4243))
        verify(bot.cooking, never()).addExperience(anyDouble())
    }
}
