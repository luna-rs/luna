package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.BlowGlassBotScript.Companion.GlassData
import game.bot.scripts.skills.BlowGlassBotScript.Companion.MOLTEN_GLASS
import game.bot.scripts.skills.BlowGlassBotScript.Companion.PIPE
import game.skill.crafting.glassMaking.GlassBlowingActionItem
import game.skill.crafting.glassMaking.GlassBlowingInterface
import game.skill.crafting.glassMaking.GlassMaterial
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class BlowGlassBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun recipesRequirePermanentLevelGlassAndPipeAndBothFactoryModesSelectGlassblowing() {
        for (material in GlassMaterial.entries) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.crafting.staticLevel).thenReturn(material.level - 1)
            InventoryProductionFixtures.bank(bot, Item(PIPE), Item(MOLTEN_GLASS, 100))
            val script = BlowGlassBotScript(bot, material, 10.minutes)
            assertFalse(script.isEligible())
            `when`(bot.crafting.staticLevel).thenReturn(material.level)
            assertTrue(script.isEligible())
            assertEquals(listOf(Item(PIPE), Item(MOLTEN_GLASS, 27)), script.bankBatch())
            for (training in listOf(false, true)) {
                val chosen = CraftingScriptFactory.getProductionScript(bot, material.level, training)
                assertInstanceOf(BlowGlassBotScript::class.java, chosen)
                assertTrue((chosen as BlowGlassBotScript).requiredLevel <= material.level)
            }
        }
    }

    @Test fun partialBankStockAndMissingSuppliesUseCorrectBatchAndBulkTargets() = runBlocking<Unit> {
        for (missing in listOf(PIPE, MOLTEN_GLASS)) {
            val bot = InventoryProductionFixtures.bot()
            val owned = if (missing == PIPE) MOLTEN_GLASS else PIPE
            InventoryProductionFixtures.bank(bot, Item(owned))
            val script = InventoryProductionFixtures.active(BlowGlassBotScript(bot, GlassMaterial.VIAL, 10.minutes))
            assertFalse(script.isEligible())
            assertTrue(script.bankBatch().isEmpty())
            assertFalse(script.onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(missing, if (missing == PIPE) 3 else 1_000)
            verify(bot.preferences, never()).raiseWantedItemTarget(eq(owned), anyInt())
        }
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(PIPE), Item(MOLTEN_GLASS, 3))
        assertEquals(listOf(Item(PIPE), Item(MOLTEN_GLASS, 3)),
            BlowGlassBotScript(bot, GlassMaterial.VIAL, 10.minutes).bankBatch())
    }

    @Test fun everyMaterialUsesItsGlassblowingButtonAndRetainsThePipe() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for (material in GlassMaterial.entries) {
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(GlassBlowingInterface::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(PIPE).onItem(MOLTEN_GLASS)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(PIPE), Item(MOLTEN_GLASS, 10))
                val output = bot.botClient.output
                doAnswer {
                    val action = GlassBlowingActionItem(bot, material, 10)
                    bot.actions.submit(action)
                    repeat(9) { assertFalse(action.run()) }
                    assertTrue(action.run())
                    null
                }.`when`(output).clickButton(material.make10Id)
                val script = InventoryProductionFixtures.active(BlowGlassBotScript(bot, material, 10.minutes))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                verify(output).clickButton(material.make10Id)
                assertEquals(10, bot.inventory.computeAmountForId(material.id))
                assertEquals(1, bot.inventory.computeAmountForId(PIPE))
                assertFalse(bot.inventory.contains(MOLTEN_GLASS))
                verify(bot.crafting, times(10)).addExperience(material.exp)
                assertEquals(0, script.snapshot().failures)
            }
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    @Test fun smallRemainingBatchesUseMakeOneOrFiveWithoutCustomInput() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            val material = GlassMaterial.UNPOWERED_ORB
            for ((stock, amount) in listOf(3 to 1, 7 to 5)) {
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(GlassBlowingInterface::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(PIPE).onItem(MOLTEN_GLASS)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(PIPE), Item(MOLTEN_GLASS, stock))
                val output = bot.botClient.output
                val button = if (amount == 1) material.make1Id else material.make5Id
                doAnswer {
                    val action = GlassBlowingActionItem(bot, material, amount)
                    bot.actions.submit(action)
                    repeat(amount) { action.run() }
                    null
                }.`when`(output).clickButton(button)
                assertTrue(withTimeout(5_000) { InventoryProductionFixtures.active(
                    BlowGlassBotScript(bot, material, 10.minutes)).executeInZone() })
                assertEquals(amount, bot.inventory.computeAmountForId(material.id))
                assertEquals(stock - amount, bot.inventory.computeAmountForId(MOLTEN_GLASS))
                verify(output).clickButton(button)
            }
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    @Test fun fullInventoryAvoidsBankingAndTemporaryLevelLossStopsWithSuppliesIntact() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val material = GlassMaterial.LANTERN_LENS
        InventoryProductionFixtures.inventory(bot, Item(PIPE), Item(MOLTEN_GLASS, 27))
        assertTrue(bot.inventory.isFull)
        val script = InventoryProductionFixtures.active(BlowGlassBotScript(bot, material, 10.minutes))
        assertFalse(script.onBankRequested(false))
        `when`(bot.crafting.level).thenReturn(material.level - 1)
        assertTrue(script.executeInZone())
        assertTrue(script.isTerminated())
        assertEquals(27, bot.inventory.computeAmountForId(MOLTEN_GLASS))
        verify(bot.crafting, never()).addExperience(anyDouble())
    }

    @Test fun unsafeStartupAndRetryBudgetsAreBoundedAcrossRestoration() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(PIPE), Item(MOLTEN_GLASS))
        for (state in 0..2) {
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            assertFalse(InventoryProductionFixtures.active(BlowGlassBotScript(bot, GlassMaterial.VIAL, 10.minutes)).onInit(false))
        }
        `when`(bot.combat.inCombat()).thenReturn(false)
        val banking = InventoryProductionFixtures.active(BlowGlassBotScript(bot, GlassMaterial.VIAL, 10.minutes))
        assertTrue(banking.onInit(false))
        repeat(3) { assertTrue(banking.onBankRequested(false)) }
        assertFalse(banking.onBankRequested(false))
        assertFalse(InventoryProductionFixtures.active(BlowGlassBotScript(bot, banking.snapshot())).onInit(true))
        InventoryProductionFixtures.inventory(bot, Item(PIPE), Item(MOLTEN_GLASS))
        `when`(bot.personality.isDextrous).thenReturn(true)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        val failed = InventoryProductionFixtures.active(BlowGlassBotScript(bot, GlassMaterial.VIAL, 10.minutes))
        repeat(3) { failed.executeInZone() }
        assertTrue(failed.isTerminated())
        assertEquals(3, failed.snapshot().failures)
        assertFalse(InventoryProductionFixtures.active(BlowGlassBotScript(bot, failed.snapshot())).onInit(true))
    }

    @Test fun sharedRegistryAndJsonRestoreMaterialZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = GlassData().apply {
                recipe = GlassMaterial.FISHBOWL.name
                duration = 7.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject()
            data.save(json)
            val loaded = GlassData().apply { load(json) }
            val restored = registry.loadScript(BlowGlassBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), loaded) as BlowGlassBotScript
            val snapshot = restored.snapshot()
            assertEquals(data.recipe, snapshot.recipe)
            assertEquals(data.duration, snapshot.duration)
            assertEquals(data.zones, snapshot.zones)
            assertEquals(data.failures, snapshot.failures)
            assertEquals(data.bankFailures, snapshot.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(manager) }
    }
}
