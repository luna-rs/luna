package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.FletchingScriptFactory
import game.bot.scripts.skills.StringBowBotScript.Companion.StringBowData
import game.skill.fletching.stringBow.Bow
import game.skill.fletching.stringBow.StringBowAction
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class StringBowBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.fletching.staticLevel).thenReturn(99)
        `when`(it.fletching.level).thenReturn(99)
    }

    @ParameterizedTest @EnumSource(value = Bow::class, names = ["ARROW_SHAFT"], mode = EnumSource.Mode.EXCLUDE)
    fun everyOwnedRecipeQualifiesWithOnePairAndProducesNativeExperience(recipe: Bow) {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, recipe.unstrungItem, Item(Bow.BOW_STRING))
        val script = StringBowBotScript(bot, recipe, 10.minutes)
        assertTrue(script.isEligible())
        InventoryProductionFixtures.execute(bot, StringBowAction(bot, recipe, 1))
        assertTrue(bot.inventory.contains(recipe.strung))
        assertFalse(bot.inventory.contains(recipe.unstrung))
        assertFalse(bot.inventory.contains(Bow.BOW_STRING))
        verify(bot.fletching).addExperience(recipe.exp)
    }

    @ParameterizedTest @EnumSource(value = Bow::class, names = ["ARROW_SHAFT"], mode = EnumSource.Mode.EXCLUDE)
    fun factorySelectsOnlyTheOwnedVariantInBothModesAndBanksPartialPairs(recipe: Bow) {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(recipe.unstrung, 3), Item(Bow.BOW_STRING, 100))
        val script = StringBowBotScript(bot, recipe, 10.minutes)
        assertEquals(listOf(Item(recipe.unstrung, 3), Item(Bow.BOW_STRING, 3)), script.bankBatch())
        for (training in listOf(true, false)) repeat(5) {
            val selected = FletchingScriptFactory.getScript(bot, 99, mutableListOf(), training)
            assertInstanceOf(StringBowBotScript::class.java, selected)
            assertEquals(recipe, (selected as StringBowBotScript).bow)
        }
        InventoryProductionFixtures.bank(bot, Item(recipe.unstrung, 100))
        assertEquals(listOf(Item(recipe.unstrung, 14), Item(Bow.BOW_STRING, 14)), script.bankBatch())
    }

    @Test fun allRecipesOpenTheirNativeDialogueAndRequestTheCarriedBalancedBatch() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        try {
            for (recipe in Bow.entries.filter { it != Bow.ARROW_SHAFT }) {
                val bot = bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(Bow.BOW_STRING).onItem(recipe.unstrung)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(recipe.unstrung, 3), Item(Bow.BOW_STRING, 5))
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = StringBowAction(bot, recipe, 3)
                    bot.actions.submit(action)
                    repeat(2) { assertFalse(action.run()) }
                    assertTrue(action.run())
                    null
                }.`when`(widgets).clickMakeItem(0, 3)
                val script = InventoryProductionFixtures.active(StringBowBotScript(bot, recipe, 10.minutes))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                verify(widgets).clickMakeItem(0, 3)
                assertEquals(3, bot.inventory.computeAmountForId(recipe.strung))
                assertEquals(2, bot.inventory.computeAmountForId(Bow.BOW_STRING))
                assertEquals(0, script.snapshot().failures)
                verify(bot.fletching, times(3)).addExperience(recipe.exp)
            }
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun missingMatchingPairsAndLockedLevelsDoNotSelectStringing() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Bow.MAGIC_LONGBOW.unstrungItem)
        assertNull(FletchingScriptFactory.getOwnedBowScript(bot, 99))
        assertInstanceOf(CutLogBotScript::class.java, FletchingScriptFactory.getTrainingScript(bot, 99, mutableListOf()))
        InventoryProductionFixtures.bank(bot, Item(Bow.BOW_STRING))
        assertNull(FletchingScriptFactory.getOwnedBowScript(bot, 84))
        `when`(bot.fletching.staticLevel).thenReturn(84)
        assertNull(FletchingScriptFactory.getOwnedBowScript(bot, 99))
        assertTrue(StringBowBotScript(bot, Bow.SHORTBOW, 10.minutes).bankBatch().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { StringBowBotScript(bot, Bow.ARROW_SHAFT, 10.minutes) }
    }

    @Test fun missingInputsRequestRealisticTotalTargetsAndOwnedInventoryCanStart() = runBlocking<Unit> {
        for (missing in listOf(Bow.OAK_LONGBOW.unstrung, Bow.BOW_STRING)) {
            val bot = bot()
            val owned = if (missing == Bow.BOW_STRING) Bow.OAK_LONGBOW.unstrung else Bow.BOW_STRING
            InventoryProductionFixtures.inventory(bot, Item(owned))
            val script = InventoryProductionFixtures.active(StringBowBotScript(bot, Bow.OAK_LONGBOW, 10.minutes))
            assertFalse(script.onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(missing, 1_000)
            verify(bot.preferences, never()).raiseWantedItemTarget(eq(owned), anyInt())
        }
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Bow.SHORTBOW.unstrungItem, Item(Bow.BOW_STRING))
        assertTrue(InventoryProductionFixtures.active(StringBowBotScript(bot, Bow.SHORTBOW, 10.minutes)).onInit(false))
    }

    @Test fun verifiedWithdrawalRequiresActualInputsAndRefreshesThePartialBatch() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Bow.YEW_LONGBOW.unstrung, 2), Item(Bow.BOW_STRING, 10))
        val script = InventoryProductionFixtures.active(StringBowBotScript(bot, Bow.YEW_LONGBOW, 10.minutes))
        val batch = script.bankBatch()
        val banking = bot.actionHandler.banking
        doAnswer {
            for (item in batch) {
                assertTrue(bot.bank.remove(item))
                assertTrue(bot.inventory.add(item))
            }
            true
        }.`when`(banking).withdrawAll(batch)
        script.onBankOpen(true)
        assertTrue(bot.inventory.containsAll(batch))
        assertFalse(script.isTerminated())
        assertFalse(script.onBankRequested(false))

        val another = bot()
        InventoryProductionFixtures.bank(another, Bow.SHORTBOW.unstrungItem, Item(Bow.BOW_STRING))
        val failed = InventoryProductionFixtures.active(StringBowBotScript(another, Bow.SHORTBOW, 10.minutes))
        `when`(another.actionHandler.banking.withdrawAll(failed.bankBatch())).thenReturn(true)
        failed.onBankOpen(true)
        assertTrue(failed.isTerminated())
    }

    @Test fun startupRejectsUnsafeBotsAndCurrentLevelLossStopsProduction() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Bow.MAGIC_LONGBOW.unstrungItem, Item(Bow.BOW_STRING))
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertFalse(InventoryProductionFixtures.active(StringBowBotScript(bot, Bow.MAGIC_LONGBOW, 10.minutes)).onInit(false))
        `when`(bot.combat.inCombat()).thenReturn(false)
        val script = InventoryProductionFixtures.active(StringBowBotScript(bot, Bow.MAGIC_LONGBOW, 10.minutes))
        assertFalse(script.onBankRequested(false))
        `when`(bot.fletching.level).thenReturn(84)
        script.executeInZone()
        assertTrue(script.isTerminated())
    }

    @Test fun centralRegistrationAndLegacySnapshotsPreserveBowZonesAndBudgets() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val original = StringBowData().apply {
                bow = Bow.MAPLE_LONGBOW
                duration = 8.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject().also { original.save(it) }
            val data = StringBowData().apply { load(json) }
            val restored = registry.loadScript(StringBowBotScript::class.qualifiedName, bot(), data)
            assertInstanceOf(StringBowBotScript::class.java, restored)
            val snapshot = (restored as StringBowBotScript).snapshot()
            assertEquals(original.bow, snapshot.bow)
            assertEquals(original.duration, snapshot.duration)
            assertEquals(original.zones, snapshot.zones)
            assertEquals(original.failures, snapshot.failures)
            assertEquals(original.bankFailures, snapshot.bankFailures)
            json.remove("failures")
            json.remove("bankFailures")
            val legacy = StringBowData().apply { load(json) }
            assertEquals(0, legacy.failures)
            assertEquals(0, legacy.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }

    @Test fun failedInteractionsKeepTheSharedRetryBudgetOnResume() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Bow.SHORTBOW.unstrungItem, Item(Bow.BOW_STRING))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(StringBowBotScript(bot, Bow.SHORTBOW, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(StringBowBotScript(bot, script.snapshot())).onInit(true))
    }
}
