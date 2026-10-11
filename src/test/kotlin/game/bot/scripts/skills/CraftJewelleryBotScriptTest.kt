package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.CraftJewelleryBotScript.Companion.CraftJewelleryData
import game.bot.scripts.skills.CraftJewelleryBotScript.Companion.RECIPES
import game.skill.crafting.jewelleryMaking.*
import game.skill.smithing.BarType
import io.luna.game.model.Locatable
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class CraftJewelleryBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun catalogueUsesExactlyTheExistingTablesAndWidgetSlots() {
        val gold = GoldJewelleryTable.VALUES.flatMap { it.jewelleryItems }.map { it.id }
        val silver = SilverJewelleryTable.VALUES.map { it.jewelleryItem.id }
        assertEquals((gold + silver).toSet(), RECIPES.keys)
        for (table in GoldJewelleryTable.VALUES) table.jewelleryItems.forEachIndexed { index, item ->
            val recipe = RECIPES.getValue(item.id)
            assertSame(item, recipe.jewellery)
            assertEquals(BarType.GOLD, recipe.bar)
            assertEquals(table.mouldId, recipe.mould)
            assertEquals(table.buttonWidgetId, recipe.widget)
            assertEquals(index, recipe.index)
        }
        for (table in SilverJewelleryTable.VALUES) {
            val recipe = RECIPES.getValue(table.jewelleryItem.id)
            assertSame(table.jewelleryItem, recipe.jewellery)
            assertEquals(BarType.SILVER, recipe.bar)
            assertEquals(table.mouldId, recipe.mould)
            assertEquals(table.mouldWidgetId, recipe.widget)
            assertEquals(0, recipe.index)
        }
    }

    @Test fun allRecipesReserveTheMouldAndBothFactoryModesRequireLevelAndOwnedInputs() {
        for ((id, recipe) in RECIPES) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.crafting.staticLevel).thenReturn(recipe.jewellery.level - 1)
            InventoryProductionFixtures.bank(bot, Item(recipe.mould), Item(recipe.bar.id, 100))
            recipe.jewellery.requiredItem?.let { InventoryProductionFixtures.bank(bot, Item(it.id, 100)) }
            val script = CraftJewelleryBotScript(bot, id, 10.minutes)
            assertFalse(script.isEligible())
            `when`(bot.crafting.staticLevel).thenReturn(recipe.jewellery.level)
            assertTrue(script.isEligible())
            val amount = if (recipe.jewellery.requiredItem == null) 27 else 13
            assertEquals(listOfNotNull(Item(recipe.mould), Item(recipe.bar.id, amount),
                recipe.jewellery.requiredItem?.let { Item(it.id, amount) }), script.bankBatch())
            for (training in listOf(false, true)) {
                val chosen = CraftingScriptFactory.getProductionScript(bot, recipe.jewellery.level, training)
                assertInstanceOf(CraftJewelleryBotScript::class.java, chosen)
                assertTrue((chosen as CraftJewelleryBotScript).requiredLevel <= recipe.jewellery.level)
            }
        }
    }

    @Test fun missingGemBarOrMouldRequestsOnlyTheMissingSupplyAtRealisticTargets() = runBlocking<Unit> {
        val recipe = RECIPES.getValue(1637)
        val inputs = listOf(recipe.mould, recipe.bar.id, recipe.jewellery.requiredItem!!.id)
        for (missing in inputs) {
            val bot = InventoryProductionFixtures.bot()
            inputs.filter { it != missing }.forEach { InventoryProductionFixtures.inventory(bot, Item(it)) }
            val script = InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, 1637, 10.minutes))
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            assertFalse(script.onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(missing, if (missing == recipe.mould) 3 else 1_000)
            inputs.filter { it != missing }.forEach {
                verify(bot.preferences, never()).raiseWantedItemTarget(eq(it), anyInt())
            }
        }
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(recipe.mould), Item(recipe.bar.id, 100),
            Item(recipe.jewellery.requiredItem!!.id, 3))
        assertEquals(listOf(Item(recipe.mould), Item(recipe.bar.id, 3), Item(recipe.jewellery.requiredItem!!.id, 3)),
            CraftJewelleryBotScript(bot, 1637, 10.minutes).bankBatch())
    }

    @Test fun everyRecipeUsesItsMetalInterfaceWidgetAndSlotWithMakeTen() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for ((id, recipe) in RECIPES) {
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                val overlay = if (recipe.bar == BarType.GOLD) GoldJewelleryInterface::class.java else SilverJewelleryInterface::class.java
                `when`(bot.overlays.has(overlay)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val furnace = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
                `when`(furnace.position).thenReturn(SubZone.AL_KHARID_BANK.inside)
                `when`(furnace.def().name).thenReturn("Furnace")
                `when`(furnace.def().actions).thenReturn(ImmutableList.of("Smelt"))
                `when`(fixtureWorld.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(setOf(furnace))
                `when`(bot.actionHandler.inventory.useItem(recipe.bar.id).onObject(furnace)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(recipe.mould), Item(recipe.bar.id, 10))
                recipe.jewellery.requiredItem?.let { InventoryProductionFixtures.inventory(bot, Item(it.id, 10)) }
                val output = bot.botClient.output
                doAnswer {
                    val action = CraftJewelleryAction(bot, recipe.bar, recipe.jewellery, 10)
                    bot.actions.submit(action)
                    repeat(9) { assertFalse(action.run()) }
                    assertTrue(action.run())
                    true
                }.`when`(output).sendItemWidgetClick(3, recipe.index, recipe.widget, id)
                val script = InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, id, 10.minutes))
                ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
                    isAccessible = true; set(script, SubZone.AL_KHARID_BANK)
                }
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                verify(output).sendItemWidgetClick(3, recipe.index, recipe.widget, id)
                assertEquals(10, bot.inventory.computeAmountForId(id))
                assertEquals(1, bot.inventory.computeAmountForId(recipe.mould))
                assertFalse(bot.inventory.contains(recipe.bar.id))
                verify(bot.crafting, times(10)).addExperience(recipe.jewellery.xp)
                assertEquals(0, script.snapshot().failures)
            }
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
            `when`(fixtureWorld.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        }
    }

    @Test fun partialBatchesUseTheExistingMakeOneAndFiveOptions() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for ((stock, option, quantity) in listOf(Triple(3, 1, 1), Triple(7, 2, 5))) {
                val bot = InventoryProductionFixtures.bot()
                val recipe = RECIPES.getValue(5525)
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(SilverJewelleryInterface::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val furnace = mock(GameObject::class.java)
                `when`(fixtureWorld.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(setOf(furnace))
                `when`(bot.actionHandler.inventory.useItem(recipe.bar.id).onObject(furnace)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(recipe.mould), Item(recipe.bar.id, stock))
                val output = bot.botClient.output
                doAnswer {
                    val action = CraftJewelleryAction(bot, recipe.bar, recipe.jewellery, quantity)
                    bot.actions.submit(action)
                    repeat(quantity) { action.run() }
                    true
                }.`when`(output).sendItemWidgetClick(option, 0, recipe.widget, 5525)
                val script = InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, 5525, 10.minutes))
                ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
                    isAccessible = true; set(script, SubZone.FALADOR_WEST_BANK)
                }
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                assertEquals(quantity, bot.inventory.computeAmountForId(5525))
                assertEquals(stock - quantity, bot.inventory.computeAmountForId(recipe.bar.id))
                verify(output).sendItemWidgetClick(option, 0, recipe.widget, 5525)
            }
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
            `when`(fixtureWorld.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        }
    }

    @Test fun fullProcessableInventoryAvoidsBankingAndCurrentLevelLossStopsBeforeInteraction() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val recipe = RECIPES.getValue(1635)
        InventoryProductionFixtures.inventory(bot, Item(recipe.mould), Item(recipe.bar.id, 27))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, 1635, 10.minutes))
        assertFalse(script.onBankRequested(false))
        `when`(bot.crafting.level).thenReturn(recipe.jewellery.level - 1)
        assertTrue(script.executeInZone())
        assertTrue(script.isTerminated())
        assertEquals(27, bot.inventory.computeAmountForId(recipe.bar.id))
        verify(bot.actionHandler.inventory, never()).useItem(recipe.bar.id, -1)
    }

    @Test fun unsafeStartupAndBothRetryBudgetsRemainBoundedAfterRestoration() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val recipe = RECIPES.getValue(1635)
        InventoryProductionFixtures.bank(bot, Item(recipe.mould), Item(recipe.bar.id))
        for (state in 0..2) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            assertFalse(InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, 1635, 10.minutes)).onInit(false))
        }
        `when`(bot.combat.inCombat()).thenReturn(false)
        val bank = InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, 1635, 10.minutes))
        assertTrue(bank.onInit(false))
        repeat(3) { assertTrue(bank.onBankRequested(false)) }
        assertFalse(bank.onBankRequested(false))
        assertFalse(InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, bank.snapshot())).onInit(true))
        InventoryProductionFixtures.inventory(bot, Item(recipe.mould), Item(recipe.bar.id))
        `when`(bot.personality.isDextrous).thenReturn(true)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        val failed = InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, 1635, 10.minutes))
        repeat(3) { failed.executeInZone() }
        assertEquals(3, failed.snapshot().failures)
        assertTrue(failed.isTerminated())
        assertFalse(InventoryProductionFixtures.active(CraftJewelleryBotScript(bot, failed.snapshot())).onInit(true))
    }

    @Test fun sharedRegistryAndJsonRestoreTheProductZonesDurationAndBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            for (id in RECIPES.keys) {
                val data = CraftJewelleryData().apply {
                    productId = id
                    duration = 7.minutes
                    zones = mutableListOf(SubZone.FALADOR_WEST_BANK)
                    failures = 2
                    bankFailures = 1
                }
                val json = JsonObject().also { data.save(it) }
                val restored = registry.loadScript(CraftJewelleryBotScript::class.qualifiedName,
                    InventoryProductionFixtures.bot(), CraftJewelleryData().apply { load(json) }) as CraftJewelleryBotScript
                assertEquals(id, restored.productId)
                assertEquals(RECIPES.getValue(id).jewellery.level, restored.requiredLevel)
                assertEquals(data.zones, restored.snapshot().zones)
                assertEquals(data.duration, restored.snapshot().duration)
                assertEquals(data.failures, restored.snapshot().failures)
                assertEquals(data.bankFailures, restored.snapshot().bankFailures)
            }
        } finally { `when`(world.botManager.scriptManager).thenReturn(manager) }
    }
}
