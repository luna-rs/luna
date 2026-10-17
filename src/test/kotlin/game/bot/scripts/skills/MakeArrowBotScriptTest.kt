package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.FletchingScriptFactory
import game.bot.scripts.skills.MakeArrowBotScript.Companion.MakeArrowData
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.attachArrow.MakeArrowActionItem
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

class MakeArrowBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(stackable: Boolean = true): Bot = InventoryProductionFixtures.bot().also { bot ->
        `when`(bot.fletching.staticLevel).thenReturn(99)
        `when`(bot.fletching.level).thenReturn(99)
        if (stackable) {
            val inventory = spy(bot.inventory)
            for (id in Arrow.entries.flatMap { listOf(it.tip, it.with, it.id) }.toSet()) {
                doReturn(true).`when`(inventory).stackable(id)
            }
            `when`(bot.inventory).thenReturn(inventory)
        }
    }

    @ParameterizedTest @EnumSource(Arrow::class)
    fun everyRecipeAcceptsPartialOwnedStacksInBothFactoryModes(recipe: Arrow) {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(recipe.tip, 16), Item(recipe.with, 2_000))
        val script = MakeArrowBotScript(bot, recipe, 10.minutes)
        assertTrue(script.isEligible())
        assertEquals(listOf(Item(recipe.tip, 16), Item(recipe.with, 16)), script.bankBatch())
        for (training in listOf(true, false)) repeat(5) {
            val selected = FletchingScriptFactory.getScript(bot, 99, mutableListOf(), training)
            assertInstanceOf(MakeArrowBotScript::class.java, selected)
            assertEquals(recipe, (selected as MakeArrowBotScript).arrow)
        }
        InventoryProductionFixtures.bank(bot, Item(recipe.tip, 2_000))
        assertEquals(listOf(Item(recipe.tip, 1_000), Item(recipe.with, 1_000)), script.bankBatch())
    }

    @ParameterizedTest @EnumSource(Arrow::class)
    fun nativePlayerActionConsumesFullAndPartialSets(recipe: Arrow) {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(recipe.tip, 16), Item(recipe.with, 20))
        val action = MakeArrowActionItem(bot, recipe, 2)
        bot.actions.submit(action)
        assertFalse(action.run())
        assertEquals(15, bot.inventory.computeAmountForId(recipe.id))
        assertTrue(action.run())
        assertEquals(16, bot.inventory.computeAmountForId(recipe.id))
        assertFalse(bot.inventory.contains(recipe.tip))
        assertEquals(4, bot.inventory.computeAmountForId(recipe.with))
        verify(bot.fletching).addExperience(recipe.exp * 15)
        verify(bot.fletching).addExperience(recipe.exp)
    }

    @Test fun allRecipesUseTheRegisteredDirectionAndRequestSetsRatherThanUnits() = runBlocking<Unit> {
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { scheduler.invoke(it.getArgument<Task>(0)); null }.`when`(world).schedule(any(Task::class.java))
        try {
            for (recipe in Arrow.entries) for (amount in listOf(1, 16, 1_000)) {
                val bot = bot()
                `when`(bot.personality.isDextrous).thenReturn(true)
                `when`(bot.overlays.player).thenReturn(bot)
                `when`(bot.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
                `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
                val used = if (recipe == Arrow.HEADLESS_ARROW) recipe.tip else recipe.with
                val target = if (recipe == Arrow.HEADLESS_ARROW) recipe.with else recipe.tip
                `when`(bot.actionHandler.inventory.useItem(used).onItem(target)).thenReturn(true)
                InventoryProductionFixtures.inventory(bot, Item(recipe.tip, amount), Item(recipe.with, amount + 5))
                val sets = (amount - 1) / Arrow.SET_AMOUNT + 1
                val xp = ArrayList<Double>()
                val skill = bot.fletching
                doAnswer { xp += it.getArgument<Double>(0); null }.`when`(skill).addExperience(anyDouble())
                val widgets = bot.actionHandler.widgets
                doAnswer {
                    val action = MakeArrowActionItem(bot, recipe, sets)
                    bot.actions.submit(action)
                    repeat(sets - 1) { assertFalse(action.run()) }
                    assertTrue(action.run())
                    null
                }.`when`(widgets).clickMakeItem(0, sets)
                val script = InventoryProductionFixtures.active(MakeArrowBotScript(bot, recipe, 10.minutes))
                assertTrue(withTimeout(5_000) { script.executeInZone() })
                verify(bot.actionHandler.inventory.useItem(used)).onItem(target)
                verify(widgets).clickMakeItem(0, sets)
                assertEquals(amount, bot.inventory.computeAmountForId(recipe.id))
                assertEquals(5, bot.inventory.computeAmountForId(recipe.with))
                assertEquals(recipe.exp * amount, xp.sum(), 0.000001)
                assertEquals(0, script.snapshot().failures)
            }
        } finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun missingInputsLevelsAndNonStackableDefinitionsAreHandled() {
        val bot = bot(false)
        val recipe = Arrow.RUNE_ARROW
        InventoryProductionFixtures.bank(bot, Item(recipe.tip, 100))
        val script = MakeArrowBotScript(bot, recipe, 10.minutes)
        assertFalse(script.isEligible())
        assertTrue(script.bankBatch().isEmpty())
        InventoryProductionFixtures.bank(bot, Item(recipe.with, 100))
        assertEquals(listOf(Item(recipe.tip, 14), Item(recipe.with, 14)), script.bankBatch())
        assertNull(FletchingScriptFactory.getOwnedArrowScript(bot, 74))
        `when`(bot.fletching.staticLevel).thenReturn(74)
        assertFalse(script.isEligible())
        assertNull(FletchingScriptFactory.getOwnedArrowScript(bot, 99))
    }

    @Test fun fullInventoryBanksUnlessTheNextSetCanReuseAnInputOrOutputSlot() = runBlocking<Unit> {
        for ((input, output, filler, shouldBank) in listOf(
            listOf(100, 0, 26, 1), listOf(1, 0, 26, 0), listOf(100, 1, 25, 0))) {
            val bot = bot()
            val recipe = Arrow.BRONZE_ARROW
            InventoryProductionFixtures.inventory(bot, Item(recipe.tip, input), Item(recipe.with, 100), Item(1511, filler))
            if (output > 0) InventoryProductionFixtures.inventory(bot, Item(recipe.id, output))
            assertTrue(bot.inventory.isFull)
            val script = InventoryProductionFixtures.active(MakeArrowBotScript(bot, recipe, 10.minutes))
            assertEquals(shouldBank == 1, script.onBankRequested(false))
        }
    }

    @Test fun capacityChecksAlsoHandleNonStackableDefinitions() = runBlocking<Unit> {
        val recipe = Arrow.BRONZE_ARROW
        val loose = bot(false)
        InventoryProductionFixtures.inventory(loose, Item(recipe.tip, 14), Item(recipe.with, 14))
        assertTrue(loose.inventory.isFull)
        assertFalse(InventoryProductionFixtures.active(MakeArrowBotScript(loose, recipe, 10.minutes)).onBankRequested(false))

        val mixed = bot()
        val mixedInventory = mixed.inventory
        doReturn(false).`when`(mixedInventory).stackable(recipe.id)
        InventoryProductionFixtures.inventory(mixed, Item(recipe.tip, 100), Item(recipe.with, 100), Item(1511, 26))
        assertTrue(mixed.inventory.isFull)
        assertTrue(InventoryProductionFixtures.active(MakeArrowBotScript(mixed, recipe, 10.minutes)).onBankRequested(false))
    }

    @Test fun onePairCanStartAndOnlyMissingInputsRequestStockTargets() = runBlocking<Unit> {
        for (missing in listOf(Arrow.HEADLESS_ARROW.tip, Arrow.HEADLESS_ARROW.with)) {
            val bot = bot()
            val owned = if (missing == Arrow.HEADLESS_ARROW.tip) Arrow.HEADLESS_ARROW.with else Arrow.HEADLESS_ARROW.tip
            InventoryProductionFixtures.inventory(bot, Item(owned))
            val script = InventoryProductionFixtures.active(MakeArrowBotScript(bot, Arrow.HEADLESS_ARROW, 10.minutes))
            assertFalse(script.onInit(false))
            verify(bot.preferences).raiseWantedItemTarget(missing, 1_000)
            verify(bot.preferences, never()).raiseWantedItemTarget(eq(owned), anyInt())
        }
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(Arrow.HEADLESS_ARROW.tip), Item(Arrow.HEADLESS_ARROW.with))
        assertTrue(InventoryProductionFixtures.active(MakeArrowBotScript(bot, Arrow.HEADLESS_ARROW, 10.minutes)).onInit(false))
    }

    @Test fun bankingWithdrawsTheActualPartialStacks() = runBlocking<Unit> {
        val bot = bot()
        val recipe = Arrow.HEADLESS_ARROW
        InventoryProductionFixtures.bank(bot, Item(recipe.tip, 16), Item(recipe.with, 1_000))
        val script = InventoryProductionFixtures.active(MakeArrowBotScript(bot, recipe, 10.minutes))
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
        assertEquals(16, bot.inventory.computeAmountForId(recipe.tip))
        assertEquals(16, bot.inventory.computeAmountForId(recipe.with))
        assertFalse(script.isTerminated())
        assertFalse(script.onBankRequested(false))
    }

    @Test fun legacyAndRegisteredSnapshotsRetainRecipeZonesAndRetryCounters() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            val original = MakeArrowData().apply {
                arrow = Arrow.ADAMANT_ARROW
                duration = 8.minutes
                zones = mutableListOf(SubZone.DRAYNOR_MAIN)
                failures = 2
                bankFailures = 1
            }
            val json = JsonObject().also { original.save(it) }
            val restored = registry.loadScript(MakeArrowBotScript::class.qualifiedName, bot(), MakeArrowData().apply { load(json) })
            assertInstanceOf(MakeArrowBotScript::class.java, restored)
            val snapshot = (restored as MakeArrowBotScript).snapshot()
            assertEquals(original.arrow, snapshot.arrow)
            assertEquals(original.duration, snapshot.duration)
            assertEquals(original.zones, snapshot.zones)
            assertEquals(original.failures, snapshot.failures)
            assertEquals(original.bankFailures, snapshot.bankFailures)
            json.remove("failures")
            json.remove("bankFailures")
            val legacy = MakeArrowData().apply { load(json) }
            assertEquals(0, legacy.failures)
            assertEquals(0, legacy.bankFailures)
        } finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
    }

    @Test fun currentLevelLossAndSavedFailuresStopProduction() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(Arrow.RUNE_ARROW.tip), Item(Arrow.RUNE_ARROW.with))
        val lowered = InventoryProductionFixtures.active(MakeArrowBotScript(bot, Arrow.RUNE_ARROW, 10.minutes))
        `when`(bot.fletching.level).thenReturn(74)
        lowered.executeInZone()
        assertTrue(lowered.isTerminated())
        `when`(bot.fletching.level).thenReturn(99)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        `when`(bot.personality.isDextrous).thenReturn(true)
        val script = InventoryProductionFixtures.active(MakeArrowBotScript(bot, Arrow.RUNE_ARROW, 10.minutes))
        repeat(3) { script.executeInZone() }
        assertEquals(3, script.snapshot().failures)
        assertTrue(script.isTerminated())
        assertFalse(InventoryProductionFixtures.active(MakeArrowBotScript(bot, script.snapshot())).onInit(true))
    }
}
