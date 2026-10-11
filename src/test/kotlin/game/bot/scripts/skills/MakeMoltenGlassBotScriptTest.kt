package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.MakeMoltenGlassBotScript.Companion.SODA_ASH
import game.bot.scripts.skills.MakeMoltenGlassBotScript.Companion.BUCKET_OF_SAND
import game.bot.scripts.skills.MakeMoltenGlassBotScript.Companion.DEFAULT_ZONES
import game.bot.scripts.skills.MakeMoltenGlassBotScript.Companion.MoltenGlassData
import game.skill.crafting.glassMaking.MakeMoltenGlassActionItem
import io.luna.game.action.ActionType
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

class MakeMoltenGlassBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun bothFactoryModesSelectOwnedPairsAndBankBatchesUseTheSmallerStock() {
        val bot = InventoryProductionFixtures.bot()
        val script = MakeMoltenGlassBotScript(bot, 10.minutes)
        InventoryProductionFixtures.bank(bot, Item(SODA_ASH, 100))
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(BUCKET_OF_SAND, 3))
        assertTrue(script.isEligible())
        assertEquals(listOf(Item(SODA_ASH, 3), Item(BUCKET_OF_SAND, 3)), script.bankBatch())
        InventoryProductionFixtures.bank(bot, Item(BUCKET_OF_SAND, 100))
        assertEquals(listOf(Item(SODA_ASH, 14), Item(BUCKET_OF_SAND, 14)), script.bankBatch())
        `when`(bot.crafting.staticLevel).thenReturn(1)
        for (training in listOf(false, true)) assertInstanceOf(MakeMoltenGlassBotScript::class.java,
            CraftingScriptFactory.getProductionScript(bot, 1, training))
        `when`(bot.crafting.staticLevel).thenReturn(0)
        assertFalse(script.isEligible())
        assertNull(CraftingScriptFactory.getProductionScript(bot, 0, true))
    }

    @Test fun missingStartupIngredientsRequestBulkTargetsWhileInventoryPairsQualify() = runBlocking<Unit> {
        for (missing in listOf(SODA_ASH, BUCKET_OF_SAND)) {
            val bot = InventoryProductionFixtures.bot()
            val owned = if (missing == SODA_ASH) BUCKET_OF_SAND else SODA_ASH
            InventoryProductionFixtures.inventory(bot, Item(owned))
            val script = InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes))
            assertFalse(script.onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(missing, 1_000)
            verify(bot.preferences, never()).raiseWantedItemTarget(owned, 1_000)
            InventoryProductionFixtures.inventory(bot, Item(missing))
            assertTrue(MakeMoltenGlassBotScript(bot, 10.minutes).isEligible())
        }
    }

    @Test fun furnaceDialogueProcessesFullAndPartialBatchesOnBothExistingRoutes() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            for (zone in DEFAULT_ZONES) for (amount in listOf(1, 14)) {
                val bot = InventoryProductionFixtures.bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val furnace = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
                `when`(furnace.position).thenReturn(zone.inside)
                `when`(furnace.def().name).thenReturn("Furnace")
                `when`(furnace.def().actions).thenReturn(ImmutableList.of("Smelt"))
                val decoy = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
                `when`(decoy.position).thenReturn(zone.inside)
                `when`(decoy.def().name).thenReturn("Range")
                `when`(decoy.def().actions).thenReturn(ImmutableList.of("Smelt"))
                val locator = fixtureWorld.locator
                doAnswer { invocation ->
                    val predicate = invocation.getArgument<Predicate<GameObject>>(2)
                    assertFalse(predicate.test(decoy))
                    assertTrue(predicate.test(furnace))
                    setOf(furnace)
                }.`when`(locator).findObjects(eq(zone.area.centerPosition), eq(zone.area.tileRadius), any())
                `when`(bot.actionHandler.inventory.useItem(SODA_ASH).onObject(furnace)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(SODA_ASH, amount), Item(BUCKET_OF_SAND, amount))
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = MakeMoltenGlassActionItem(bot, amount)
                    bot.actions.submit(action)
                    repeat(amount - 1) { assertFalse(action.run()) }
                    assertTrue(action.run())
                    null
                }.`when`(widgets).clickMakeItem(0, amount)
                val script = InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes))
                ZonedBotScript::class.java.getDeclaredField("activeZone").apply { isAccessible = true; set(script, zone) }
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                verify(widgets).clickMakeItem(0, amount)
                assertEquals(amount, bot.inventory.computeAmountForId(1775))
                assertEquals(amount, bot.inventory.computeAmountForId(1925))
                assertFalse(bot.inventory.contains(SODA_ASH))
                assertFalse(bot.inventory.contains(BUCKET_OF_SAND))
                verify(bot.crafting, times(amount)).addExperience(20.0)
                assertEquals(0, script.snapshot().failures)
            }
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
            `when`(fixtureWorld.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        }
    }

    @Test fun processableFullInventoryAvoidsBankingAndExhaustedInputsRequestBanking() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(SODA_ASH, 14), Item(BUCKET_OF_SAND, 14))
        val script = InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes))
        assertTrue(bot.inventory.isFull)
        assertFalse(script.onBankRequested(false))
        bot.inventory.remove(Item(SODA_ASH, 14))
        assertTrue(script.onBankRequested(false))
    }

    @Test fun unsafeStatesPreventStartupAndCurrentLevelLossStopsBeforeInteraction() = runBlocking<Unit> {
        for (state in 0..3) {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.bank(bot, Item(SODA_ASH), Item(BUCKET_OF_SAND))
            `when`(bot.health).thenReturn(if (state == 0) 0 else 99)
            `when`(bot.isLocked).thenReturn(state == 1)
            `when`(bot.combat.inCombat()).thenReturn(state == 2)
            if (state == 3) bot.actions.submit(object : io.luna.game.action.Action<io.luna.game.model.mob.bot.Bot>(
                bot, ActionType.STRONG, true, 1) {
                override fun run() = false
            })
            assertFalse(InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes)).onInit(false))
        }
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(SODA_ASH), Item(BUCKET_OF_SAND))
        `when`(bot.crafting.level).thenReturn(0)
        val script = InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes))
        assertTrue(script.executeInZone())
        assertTrue(script.isTerminated())
        verify(bot.actionHandler.inventory, never()).useItem(SODA_ASH, -1)
        assertTrue(bot.inventory.contains(BUCKET_OF_SAND))
    }

    @Test fun missingFurnacesExhaustInteractionBudgetAndRestorationCannotResetIt() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(SODA_ASH), Item(BUCKET_OF_SAND))
        `when`(bot.personality.isDextrous).thenReturn(true)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
        `when`(world.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenReturn(emptySet())
        val script = InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes))
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
            isAccessible = true; set(script, SubZone.AL_KHARID_BANK)
        }
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        assertEquals(3, script.snapshot().failures)
        assertFalse(InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, script.snapshot())).onInit(true))
    }

    @Test fun bankingBudgetIsPersistentAndActualWithdrawalMustMatchRequestedInputs() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(SODA_ASH), Item(BUCKET_OF_SAND))
        val script = InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes))
        assertTrue(script.onInit(false))
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        assertFalse(InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, script.snapshot())).onInit(true))
        val fresh = InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes))
        val requested = listOf(Item(SODA_ASH), Item(BUCKET_OF_SAND))
        `when`(bot.actionHandler.banking.withdrawAll(requested)).thenReturn(true)
        fresh.onBankOpen(false)
        assertTrue(fresh.isTerminated())
        InventoryProductionFixtures.inventory(bot, *requested.toTypedArray())
        val successful = InventoryProductionFixtures.active(MakeMoltenGlassBotScript(bot, 10.minutes))
        assertTrue(successful.onInit(false))
        assertTrue(successful.onBankRequested(false))
        successful.onBankOpen(false)
        assertFalse(successful.isTerminated())
        assertEquals(0, successful.snapshot().bankFailures)
        assertFalse(successful.onBankRequested(false))
        verify(bot.actionHandler.banking, times(2)).clickBankingMode(false)
    }

    @Test fun sharedRegistrationAndJsonRestoreDurationZonesAndRetryBudgets() {
        val manager = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val data = MoltenGlassData().apply {
                duration = 7.minutes
                zones = mutableListOf(SubZone.AL_KHARID_BANK)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject().also { data.save(it) }
            val restored = registry.loadScript(MakeMoltenGlassBotScript::class.qualifiedName,
                InventoryProductionFixtures.bot(), MoltenGlassData().apply { load(json) }) as MakeMoltenGlassBotScript
            assertEquals(data.duration, restored.snapshot().duration)
            assertEquals(data.zones, restored.snapshot().zones)
            assertEquals(data.failures, restored.snapshot().failures)
            assertEquals(data.bankFailures, restored.snapshot().bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(manager) }
    }
}
