package api.bot.script

import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import io.luna.game.task.Task
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class InventoryProductionTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun finalPartialBatchReservesTheToolSlot() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623, 100), Item(1755))
        assertEquals(listOf(Item(1755), Item(1623, 27)), bot.productionBatch(listOf(Item(1623)), setOf(1755)))
        bot.bank.remove(Item(1623, 97))
        assertEquals(listOf(Item(1755), Item(1623, 3)), bot.productionBatch(listOf(Item(1623)), setOf(1755)))
    }

    @Test fun pairedInputsUseSmallerStockAndInventoryCapacity() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(985, 100), Item(987, 3))
        val materials = listOf(Item(985), Item(987))
        assertEquals(listOf(Item(985, 3), Item(987, 3)), bot.productionBatch(materials))
        InventoryProductionFixtures.bank(bot, Item(987, 100))
        assertEquals(listOf(Item(985, 14), Item(987, 14)), bot.productionBatch(materials))
    }

    @Test fun missingToolsOrExhaustedMaterialsProduceNoWithdrawal() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623, 3))
        assertTrue(bot.productionBatch(listOf(Item(1623)), setOf(1755)).isEmpty())
        bot.bank.remove(Item(1623, 3))
        assertTrue(bot.productionBatch(listOf(Item(1623))).isEmpty())
    }

    @Test fun invalidRecipeQuantitiesAndDuplicateInputsAreRejected() {
        val bot = InventoryProductionFixtures.bot()
        assertThrows(IllegalArgumentException::class.java) { bot.productionBatch(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) {
            bot.productionBatch(listOf(Item(1623), Item(1623)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            bot.productionBatch(listOf(Item(1623)), setOf(1623))
        }
    }

    @Test fun ownershipIncludesInventoryAndBankAndRequiresEveryInput() {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1755))
        InventoryProductionFixtures.bank(bot, Item(1623, 3))
        assertTrue(bot.ownsProductionSupplies(listOf(Item(1623, 3)), setOf(1755)))
        assertFalse(bot.ownsProductionSupplies(listOf(Item(1623, 4)), setOf(1755)))
        assertFalse(bot.ownsProductionSupplies(listOf(Item(1623)), setOf(233)))
    }

    private open class Recipe(bot: Bot) :
        InventoryBotScript(bot, 10.minutes, mutableListOf(SubZone.HOME)) {
        init { progress = Job() }
        override fun withdraw() = listOf(Item(1623))
        override fun snapshot(): BotScriptData? = null
    }

    @Test fun successfulDefaultWithdrawalRetainsExistingBankingBehavior() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623))
        val script = Recipe(bot)
        assertTrue(script.onInit(false))
        val items = listOf(Item(1623))
        `when`(bot.actionHandler.banking.withdrawAll(items)).thenReturn(true)
        script.onBankOpen(true)
        verify(bot.actionHandler.banking).withdrawAll(items)
        assertFalse(script.isTerminated())
    }

    @Test fun unsuccessfulDefaultWithdrawalEndsTheScript() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623))
        val script = Recipe(bot)
        assertTrue(script.onInit(false))
        `when`(bot.actionHandler.banking.withdrawAll(listOf(Item(1623)))).thenReturn(false)
        script.onBankOpen(true)
        assertTrue(script.isTerminated())
    }

    @Test fun inventoryBaseUsesTheSubclassWithdrawalHook() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1623))
        var attempted: List<Item>? = null
        val script = object : Recipe(bot) {
            override suspend fun withdrawBankItems(items: List<Item>): Boolean {
                attempted = items
                return true
            }
        }
        assertTrue(script.onInit(false))
        script.onBankOpen(true)
        assertEquals(listOf(Item(1623)), attempted)
        verify(bot.actionHandler.banking, never()).withdrawAll(anyList())
        assertFalse(script.isTerminated())
    }

    private class Production(bot: Bot, data: InventoryScriptData? = null, val limit: Int = 3) : Recipe(bot) {
        override val maxFailures get() = limit
        override val verifiedProductionWithdrawals = true
        var interact: suspend (Int) -> Boolean = { false }
        var available = true
        var producedId: Int? = null
        val inputs = listOf(Item(1623))
        val tools = setOf(1755)
        init { if (data != null) restoreInventoryState(data) }
        override fun withdraw() = productionWithdraw(inputs, tools)
        override fun bankWithdraw() = bot.productionBatch(inputs, tools)
        override suspend fun onInventoryBankRequested() = requestProductionBank(bot.inventory.containsAll(inputs) &&
            tools.all { bot.inventory.contains(it) })
        override suspend fun onExecuteInZone(): Boolean {
            if (!productionReady(bot.inventory.containsAll(inputs) && tools.all { bot.inventory.contains(it) })) return true
            attemptProduction(1623, dexterityDelay = false, available = available, outputId = producedId, start = interact)
            return true
        }
        override fun snapshot() = InventoryScriptData().also { saveInventoryState(it) }
    }

    @Test fun nativeRetryStateReadsLegacyFieldsAndMissingFieldsDefaultToZero() {
        val data = InventoryScriptData()
        val legacy = JsonObject().apply {
            addProperty("duration", 600_000)
            add("zones", com.google.gson.JsonArray().apply { add("HOME") })
        }
        data.load(legacy)
        assertEquals(0, data.failures)
        assertEquals(0, data.bankFailures)
        val old = legacy.deepCopy().apply {
            addProperty("failures", 2)
            addProperty("bankFailures", 1)
        }
        data.load(old)
        val script = Production(InventoryProductionFixtures.bot(), data)
        val saved = JsonObject().also { script.snapshot().save(it) }
        assertEquals(2, saved.get("failures").asInt)
        assertEquals(1, saved.get("bankFailures").asInt)
        assertEquals(10.minutes, script.snapshot().duration)
        assertEquals(listOf(SubZone.HOME), script.snapshot().zones)
    }

    @Test fun nativeProductionUsesTheConfiguredFailureLimitAndExhaustionSurvivesRestore() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1623), Item(1755))
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(false)
        val script = Production(bot, limit = 2)
        script.executeInZone()
        assertFalse(script.isTerminated())
        assertEquals(1, script.snapshot().failures)
        script.executeInZone()
        assertTrue(script.isTerminated())
        assertEquals(2, script.snapshot().failures)
        assertEquals(0, script.snapshot().bankFailures)
        val resumed = Production(bot, script.snapshot(), limit = 2)
        assertFalse(resumed.onInit(true))
        assertTrue(resumed.isTerminated())
        verify(bot.actionHandler.widgets, times(2)).clickCloseInterface()
    }

    @Test fun actualInputProgressResetsOnlyTheInteractionBudget() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(1623, 2), Item(1755))
            `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
            val script = Production(bot, InventoryScriptData().apply { failures = 2; bankFailures = 1 })
            script.interact = { before -> assertEquals(2, before); bot.inventory.remove(1623) }
            script.executeInZone()
            assertEquals(0, script.snapshot().failures)
            assertEquals(1, script.snapshot().bankFailures)
            assertFalse(script.isTerminated())
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    @Test fun verifiedOutputProgressResetsFailuresWithoutRequiringInputConsumption() = runBlocking<Unit> {
        val fixtureWorld = world
        val scheduler = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer { invocation -> scheduler.invoke(invocation.getArgument<Task>(0)); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            val bot = InventoryProductionFixtures.bot()
            InventoryProductionFixtures.inventory(bot, Item(1623), Item(1755))
            `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
            val script = Production(bot, InventoryScriptData().apply { failures = 2; bankFailures = 1 })
            script.producedId = 1607
            script.interact = { bot.inventory.add(Item(1607)) }
            script.executeInZone()
            assertTrue(bot.inventory.contains(1623))
            assertTrue(bot.inventory.contains(1607))
            assertEquals(0, script.snapshot().failures)
            assertEquals(1, script.snapshot().bankFailures)
            assertFalse(script.isTerminated())
        } finally { doNothing().`when`(fixtureWorld).schedule(any(Task::class.java)) }
    }

    @Test fun incompleteVerifiedWithdrawalStopsWithoutResettingEitherBudget() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1755), Item(1623))
        val script = Production(bot, InventoryScriptData().apply { failures = 1; bankFailures = 2 })
        val batch = listOf(Item(1755), Item(1623))
        `when`(bot.actionHandler.banking.withdrawAll(batch)).thenReturn(true)
        script.onBankOpen(false)
        assertTrue(script.isTerminated())
        assertEquals(1, script.snapshot().failures)
        assertEquals(2, script.snapshot().bankFailures)
        verify(bot.actionHandler.banking).clickBankingMode(false)
    }

    @Test fun verifiedWithdrawalResetsOnlyBankingAndUsableStockPreventsMoreRequests() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.bank(bot, Item(1755), Item(1623))
        InventoryProductionFixtures.inventory(bot, Item(1755), Item(1623))
        val script = Production(bot, InventoryScriptData().apply { failures = 2; bankFailures = 1 })
        assertTrue(script.onInit(true))
        assertTrue(script.onBankRequested(false))
        assertEquals(2, script.snapshot().bankFailures)
        `when`(bot.actionHandler.banking.withdrawAll(listOf(Item(1755), Item(1623)))).thenReturn(true)
        script.onBankOpen(false)
        assertEquals(0, script.snapshot().bankFailures)
        assertEquals(2, script.snapshot().failures)
        assertFalse(script.onBankRequested(false))
        assertFalse(script.isTerminated())
    }

    @Test fun unsafeAndWeakActionStatesDoNotSpendRetryBudgets() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1755), Item(1623))
        val script = Production(bot)
        `when`(bot.isLocked).thenReturn(true)
        assertFalse(script.onBankRequested(false))
        script.executeInZone()
        assertEquals(0, script.snapshot().failures)
        assertEquals(0, script.snapshot().bankFailures)
        `when`(bot.isLocked).thenReturn(false)
        bot.actions.submit(object : io.luna.game.action.Action<Bot>(bot, io.luna.game.action.ActionType.WEAK) {
            override fun run() = false
        })
        assertFalse(script.onBankRequested(false))
        script.executeInZone()
        assertEquals(0, script.snapshot().failures)
        assertEquals(0, script.snapshot().bankFailures)
        verify(bot.actionHandler.widgets, never()).clickCloseInterface()
    }

    @Test fun unavailableTargetsSpendFailuresWithoutClosingAnInterface() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        InventoryProductionFixtures.inventory(bot, Item(1755), Item(1623))
        val script = Production(bot).apply { available = false }
        repeat(3) { script.executeInZone() }
        assertTrue(script.isTerminated())
        assertEquals(3, script.snapshot().failures)
        assertEquals(0, script.snapshot().bankFailures)
        verify(bot.actionHandler.widgets, never()).clickCloseInterface()
    }
}
