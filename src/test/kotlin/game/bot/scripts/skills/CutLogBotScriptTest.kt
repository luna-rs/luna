package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.FletchingScriptFactory
import game.bot.scripts.skills.CutLogBotScript.Companion.CutLogData
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.cutLog.CutLogActionItem
import game.skill.fletching.cutLog.Log
import game.skill.fletching.cutLog.Log.Companion.KNIFE
import game.skill.fletching.stringBow.Bow
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
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class CutLogBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(stackableShafts: Boolean = true): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.fletching.staticLevel).thenReturn(99)
        `when`(it.fletching.level).thenReturn(99)
        if (stackableShafts) {
            val inventory = spy(it.inventory)
            doReturn(true).`when`(inventory).stackable(Bow.ARROW_SHAFT.unstrung)
            `when`(it.inventory).thenReturn(inventory)
        }
    }

    @Test fun everyRecipeUsesItsNativeDialogueIndexAndPlayerAction() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        try {
            for (log in Log.entries) for ((index, bow) in log.bows.withIndex()) {
                val bot = bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                `when`(bot.actionHandler.inventory.useItem(KNIFE).onItem(log.id)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(KNIFE), Item(log.id, 3))
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = CutLogActionItem(bot, log.id, bow, 3)
                    bot.actions.submit(action)
                    repeat(2) { assertFalse(action.run()) }
                    assertTrue(action.run())
                    null
                }.`when`(widgets).clickMakeItem(index, 3)
                val script = InventoryProductionFixtures.active(CutLogBotScript(bot, log, index, 10.minutes))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                verify(widgets).clickMakeItem(index, 3)
                assertFalse(bot.inventory.contains(log.id))
                assertTrue(bot.inventory.contains(KNIFE))
                assertEquals(if (bow == Bow.ARROW_SHAFT) 3 * Arrow.SET_AMOUNT else 3,
                    bot.inventory.computeAmountForId(bow.unstrung))
                verify(bot.fletching, times(3)).addExperience(bow.exp)
                assertEquals(0, script.snapshot().failures)
            }
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun factoryChoosesOwnedLogTierAndUnlockedRecipeInBothModes() {
        for (log in Log.entries) for ((index, bow) in log.bows.withIndex()) {
            val bot = bot()
            `when`(bot.personality.isIntelligent).thenReturn(true)
            InventoryProductionFixtures.bank(bot, Item(KNIFE), Item(log.id, 1))
            val script = CutLogBotScript(bot, log, index, 10.minutes)
            assertTrue(script.isEligible())
            assertEquals(listOf(Item(KNIFE), Item(log.id)), script.bankBatch())
            for (training in listOf(true, false)) {
                val selected = FletchingScriptFactory.getScript(bot, bow.level, mutableListOf(), training)
                assertInstanceOf(CutLogBotScript::class.java, selected)
                assertEquals(log, (selected as CutLogBotScript).log)
                assertEquals(index, selected.index)
            }
            InventoryProductionFixtures.bank(bot, Item(log.id, 100))
            assertEquals(listOf(Item(KNIFE), Item(log.id, 27)), script.bankBatch())
        }
    }

    @Test fun invalidIndicesAndMissingToolsOrLevelsDoNotQualify() {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Log.MAGIC.id))
        val script = CutLogBotScript(bot, Log.MAGIC, 1, 10.minutes)
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        assertNull(FletchingScriptFactory.getOwnedLogScript(bot, 99))
        InventoryProductionFixtures.bank(bot, Item(KNIFE))
        assertNull(FletchingScriptFactory.getOwnedLogScript(bot, 79))
        `when`(bot.fletching.staticLevel).thenReturn(79)
        assertFalse(script.isEligible())
        assertNull(FletchingScriptFactory.getOwnedLogScript(bot, 99))
        for (log in Log.entries) {
            assertThrows(IllegalArgumentException::class.java) { CutLogBotScript(bot, log, -1, 10.minutes) }
            assertThrows(IllegalArgumentException::class.java) { CutLogBotScript(bot, log, log.bows.size, 10.minutes) }
        }
    }

    @Test fun fullInventoriesAndNonStackableShaftDefinitionsRespectOutputCapacity() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(KNIFE), Item(Log.NORMAL.id, 27))
        assertTrue(bot.inventory.isFull)
        assertFalse(InventoryProductionFixtures.active(CutLogBotScript(bot, Log.NORMAL, 0, 10.minutes)).onBankRequested(false))
        assertFalse(InventoryProductionFixtures.active(CutLogBotScript(bot, Log.NORMAL, 2, 10.minutes)).onBankRequested(false))
        val loose = bot(false)
        InventoryProductionFixtures.bank(loose, Item(KNIFE), Item(Log.NORMAL.id, 100))
        val shaftScript = InventoryProductionFixtures.active(CutLogBotScript(loose, Log.NORMAL, 0, 10.minutes))
        assertEquals(listOf(Item(KNIFE), Item(Log.NORMAL.id)), shaftScript.bankBatch())
        InventoryProductionFixtures.inventory(loose, Item(KNIFE), Item(Log.NORMAL.id, 27))
        assertTrue(shaftScript.onBankRequested(false))
    }

    @Test fun missingLogsAndKnifeUseMaterialAndToolTargets() = runBlocking<Unit> {
        for (missing in listOf(Log.OAK.id, KNIFE)) {
            val bot = bot()
            val owned = if (missing == KNIFE) Log.OAK.id else KNIFE
            InventoryProductionFixtures.inventory(bot, Item(owned))
            val script = InventoryProductionFixtures.active(CutLogBotScript(bot, Log.OAK, 0, 10.minutes))
            assertFalse(script.onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(missing, if (missing == KNIFE) 3 else 1_000)
            verify(bot.preferences, never()).raiseWantedItemTarget(eq(owned), anyInt())
        }
    }

    @Test fun bankWithdrawalsTransferPartialStockAndResetBankingState() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(KNIFE), Item(Log.YEW.id, 3))
        val script = InventoryProductionFixtures.active(CutLogBotScript(bot, Log.YEW, 0, 10.minutes))
        assertTrue(script.onInit(false))
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
    }

    @Test fun completionQueuesOnlyTheSelectedOwnedBowWithStrings() = runBlocking<Unit> {
        val bot = bot()
        `when`(bot.personality.isDextrous).thenReturn(true)
        InventoryProductionFixtures.inventory(bot, Item(Bow.ARROW_SHAFT.unstrung), Bow.SHORTBOW.unstrungItem,
            Bow.LONGBOW.unstrungItem, Item(Bow.BOW_STRING))
        CutLogBotScript(bot, Log.NORMAL, 2, 10.minutes).completed()
        val captured = ArgumentCaptor.forClass(StringBowBotScript::class.java)
        verify(bot.scriptStack).pushTail(captured.capture(), eq(2))
        assertEquals(Bow.LONGBOW, captured.value.bow)
        for ((index, supplies) in listOf(
            2 to listOf(Bow.LONGBOW.unstrungItem),
            2 to listOf(Bow.SHORTBOW.unstrungItem, Item(Bow.BOW_STRING)),
            0 to listOf(Item(Bow.ARROW_SHAFT.unstrung), Item(Bow.BOW_STRING)))) {
            val other = bot()
            `when`(other.personality.isDextrous).thenReturn(true)
            InventoryProductionFixtures.inventory(other, *supplies.toTypedArray())
            CutLogBotScript(other, Log.NORMAL, index, 10.minutes).completed()
            verifyNoInteractions(other.scriptStack)
        }
    }

    @Test fun centralRegistrationAndLegacyDataPreserveTheRecipeAndRetryBudgets() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val original = CutLogData().apply {
                log = Log.WILLOW
                index = 1
                duration = 8.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject().also { original.save(it) }
            val restored = registry.loadScript(CutLogBotScript::class.qualifiedName, bot(), CutLogData().apply { load(json) })
            assertInstanceOf(CutLogBotScript::class.java, restored)
            val snapshot = (restored as CutLogBotScript).snapshot()
            assertEquals(original.log, snapshot.log)
            assertEquals(original.index, snapshot.index)
            assertEquals(original.zones, snapshot.zones)
            assertEquals(original.duration, snapshot.duration)
            assertEquals(original.failures, snapshot.failures)
            assertEquals(original.bankFailures, snapshot.bankFailures)
            json.remove("failures")
            json.remove("bankFailures")
            val legacy = CutLogData().apply { load(json) }
            assertEquals(0, legacy.failures)
            assertEquals(0, legacy.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }

    @Test fun levelLossAndFailedInteractionsStopUsingSharedCounters() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(KNIFE), Item(Log.MAGIC.id))
        `when`(bot.fletching.level).thenReturn(79)
        val lowered = InventoryProductionFixtures.active(CutLogBotScript(bot, Log.MAGIC, 0, 10.minutes))
        lowered.executeInZone()
        assertTrue(lowered.isTerminated())
        `when`(bot.fletching.level).thenReturn(99)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(CutLogBotScript(bot, Log.MAGIC, 0, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(CutLogBotScript(bot, script.snapshot())).onInit(true))
    }
}
