package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.MakePotteryBotScript.Companion.PotteryData
import game.bot.scripts.skills.MakePotteryBotScript.Companion.Stage
import game.skill.crafting.potteryCrafting.*
import io.luna.game.model.Locatable
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

class MakePotteryBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun bothPhasesUseOwnedInputsAndBankFullOrPartialBatchesForAllRecipes() {
        for (material in Unfired.entries) for (stage in Stage.entries) {
            val bot = InventoryProductionFixtures.bot()
            val script = MakePotteryBotScript(bot, material, stage, 10.minutes)
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            InventoryProductionFixtures.bank(bot, Item(script.inputId, 3))
            assertTrue(script.isEligible())
            assertEquals(listOf(Item(script.inputId, 3)), script.bankBatch())
            InventoryProductionFixtures.bank(bot, Item(script.inputId, 100))
            assertEquals(listOf(Item(script.inputId, 28)), script.bankBatch())
            assertEquals(listOf(SubZone.BARBARIAN_VILLAGE), script.zones)
            `when`(bot.crafting.staticLevel).thenReturn(material.level - 1)
            assertFalse(script.isEligible())
        }
    }

    @Test fun bothFactoryModesSelectShapingAndFiringFromOwnedSupplies() {
        for (material in Unfired.entries) for (stage in Stage.entries) for (training in listOf(false, true)) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.crafting.staticLevel).thenReturn(material.level)
            val script = MakePotteryBotScript(bot, material, stage, 10.minutes)
            InventoryProductionFixtures.bank(bot, Item(script.inputId))
            val selected = CraftingScriptFactory.getProductionScript(bot, material.level, training)
            assertInstanceOf(MakePotteryBotScript::class.java, selected)
            selected as MakePotteryBotScript
            assertEquals(stage, selected.stage)
            assertTrue(selected.isEligible())
            if (stage == Stage.FIRE) assertEquals(material, selected.material)
        }
    }

    @Test fun missingInputRequestsABulkTargetAndOwnedInventoryQualifies() = runBlocking<Unit> {
        for (material in Unfired.entries) for (stage in Stage.entries) {
            val bot = InventoryProductionFixtures.bot()
            val script = InventoryProductionFixtures.active(MakePotteryBotScript(bot, material, stage, 10.minutes))
            assertFalse(script.onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(script.inputId, 1_000)
            InventoryProductionFixtures.inventory(bot, Item(script.inputId))
            assertTrue(script.isEligible())
        }
    }

    @Test fun everyRecipeUsesTheCorrectPlayerDialogueAndConvertsFullAndPartialInventories() = runBlocking<Unit> {
        val locator = world.locator
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(world).schedule(any(Task::class.java))
        try {
            for (material in Unfired.entries) for (stage in Stage.entries) for (amount in listOf(1, 28)) {
                val zone = SubZone.BARBARIAN_VILLAGE
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val facility = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
                `when`(facility.id).thenReturn(stage.objectIds.first())
                `when`(facility.position).thenReturn(zone.inside)
                val wrongFacility = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
                `when`(wrongFacility.id).thenReturn(if (stage == Stage.SHAPE) Stage.FIRE.objectIds.first() else Stage.SHAPE.objectIds.first())
                `when`(wrongFacility.position).thenReturn(zone.inside)
                val outside = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
                `when`(outside.id).thenReturn(stage.objectIds.first())
                `when`(outside.position).thenReturn(SubZone.HOME.inside)
                doAnswer { invocation ->
                    val predicate = invocation.getArgument<Predicate<GameObject>>(2)
                    assertTrue(predicate.test(facility))
                    assertFalse(predicate.test(wrongFacility))
                    assertFalse(predicate.test(outside))
                    setOf(facility)
                }.`when`(locator).findObjects(eq(zone.area.centerPosition), eq(zone.area.tileRadius), any())
                val script = InventoryProductionFixtures.active(MakePotteryBotScript(bot, material, stage, 10.minutes))
                `when`(bot.actionHandler.inventory.useItem(script.inputId).onObject(facility)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(script.inputId, amount))
                val index = if (stage == Stage.SHAPE) Unfired.UNFIRED_ID_ARRAY.indexOf(material.unfiredId) else 0
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = if (stage == Stage.SHAPE) PotteryWheelActionItem(bot, material, amount)
                                 else PotteryOvenActionItem(bot, material, amount)
                    bot.actions.submit(action)
                    repeat(amount - 1) { assertFalse(action.run()) }
                    assertTrue(action.run())
                    null
                }.`when`(widgets).clickMakeItem(index, amount)
                ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(script, zone) }
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                verify(bot.actionHandler.widgets).clickMakeItem(index, amount)
                assertFalse(bot.inventory.contains(script.inputId))
                val product = if (stage == Stage.SHAPE) material.unfiredId else material.firedId
                assertEquals(amount, bot.inventory.computeAmountForId(product))
                verify(bot.crafting, times(amount)).addExperience(if (stage == Stage.SHAPE) material.shapingExp else material.firingExp)
                assertEquals(0, script.snapshot().failures)
            }
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
            `when`(world.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        }
    }

    @Test fun fullProcessableInventoryDoesNotBankAndCurrentLevelLossStopsProduction() = runBlocking<Unit> {
        for (material in Unfired.entries) for (stage in Stage.entries) {
            val bot = InventoryProductionFixtures.bot()
            val script = InventoryProductionFixtures.active(MakePotteryBotScript(bot, material, stage, 10.minutes))
            InventoryProductionFixtures.inventory(bot, Item(script.inputId, 28))
            assertTrue(bot.inventory.isFull)
            assertFalse(script.onBankRequested(false))
            `when`(bot.crafting.level).thenReturn(material.level - 1)
            assertTrue(script.executeInZone())
            assertTrue(script.isTerminated())
            assertEquals(28, bot.inventory.computeAmountForId(script.inputId))
        }
    }

    @Test fun registryAndJsonRestoreBothPhasesAndExhaustedBudgets() = runBlocking<Unit> {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            for (material in Unfired.entries) for (stage in Stage.entries) {
                val bot = InventoryProductionFixtures.bot()
                val data = MakePotteryBotScript(bot, material, stage, 10.minutes).snapshot().also {
                    it.failures = 3
                    it.bankFailures = 2
                }
                val json = JsonObject().also { data.save(it) }
                val loaded = PotteryData().also { it.load(json) }
                val restored = registry.loadScript(MakePotteryBotScript::class.qualifiedName, bot, loaded)
                assertInstanceOf(MakePotteryBotScript::class.java, restored)
                restored as MakePotteryBotScript
                assertEquals(material, restored.material)
                assertEquals(stage, restored.stage)
                assertEquals(10.minutes, restored.duration)
                assertEquals(MakePotteryBotScript.DEFAULT_ZONES, restored.zones)
                assertEquals(2, restored.snapshot().bankFailures)
                InventoryProductionFixtures.active(restored)
                assertFalse(restored.onInit(true))
                assertTrue(restored.isTerminated())
            }
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(previous)
        }
    }
}
