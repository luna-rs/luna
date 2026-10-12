package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.bot.scripts.skills.FiremakingBotScript.Companion.FiremakingData
import game.skill.firemaking.Firemaking.TINDERBOX
import game.skill.firemaking.Log
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptSnapshot
import io.luna.game.model.mob.bot.script.BotScriptStack
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies shared Firemaking banking budgets and persisted retry state.
 *
 * @author lare96
 */
class FiremakingBankingBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.firemaking.staticLevel).thenReturn(99)
        `when`(it.firemaking.level).thenReturn(99)
    }

    private fun data() = FiremakingData().apply {
        duration = 7.minutes
        zones = mutableListOf(SubZone.HOME)
        log = Log.NORMAL
    }

    private fun script(bot: Bot, data: FiremakingData = data()) =
        InventoryProductionFixtures.active(FiremakingBotScript(bot, data))

    @Test fun unresolvedRequestsStopAndRemainExhaustedAfterSaving() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Log.NORMAL.id, 3), Item(TINDERBOX))
        val script = script(bot)
        script.executeInZone()
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        assertTrue(script.isTerminated())
        val saved = JsonObject().also { script.snapshot().save(it) }
        val loaded = FiremakingData().apply { load(saved) }
        assertEquals(3, loaded.bankFailures)
        val restored = script(bot, loaded)
        assertFalse(restored.onInit(true))
        assertTrue(restored.isTerminated())
    }

    @Test fun thirdRequestCanCompleteAndResetOnlyBankingFailures() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Log.NORMAL.id, 3), Item(TINDERBOX))
        val script = script(bot, data().apply { failures = 2 })
        script.executeInZone()
        repeat(3) { assertTrue(script.onBankRequested(false)) }
        val banking = bot.actionHandler.banking
        val batch = listOf(Item(TINDERBOX), Item(Log.NORMAL.id, 3))
        doAnswer {
            batch.forEach { item -> assertTrue(bot.bank.remove(item)); assertTrue(bot.inventory.add(item)) }
            true
        }.`when`(banking).withdrawAll(batch)
        script.onBankOpen(true)
        assertFalse(script.isTerminated())
        val snapshot = script.snapshot() as FiremakingData
        assertEquals(0, snapshot.bankFailures)
        assertEquals(2, snapshot.failures)
        assertFalse(script.onBankRequested(false))
    }

    @Test fun partialBudgetRestoresWithItsRemainingRequests() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Log.NORMAL.id, 3), Item(TINDERBOX))
        val saved = JsonObject().also { data().apply { bankFailures = 1 }.save(it) }
        val loaded = FiremakingData().apply { load(saved) }
        val script = script(bot, loaded)
        assertTrue(script.onInit(true))
        script.executeInZone()
        repeat(2) { assertTrue(script.onBankRequested(false)) }
        assertFalse(script.onBankRequested(false))
        assertTrue(script.isTerminated())
    }

    @ParameterizedTest @EnumSource(Log::class)
    fun legacyStackKeepsRecipeAndZonesWithoutNewCounterFields(log: Log) {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try { InventoryProductionFixtures.register("Scripts") }
        finally { `when`(world.botManager.scriptManager).thenReturn(previous) }
        val original = data().apply { this.log = log }
        val json = JsonObject().also { original.save(it); it.remove("failures"); it.remove("bankFailures") }
        val stack = BotScriptStack(bot(), registry)
        stack.load(listOf(BotScriptSnapshot(0, FiremakingBotScript::class.java.name, FiremakingData::class.java.name, json)))
        assertEquals(1, stack.size())
        val restored = stack.current() as FiremakingBotScript
        assertEquals(log, restored.log)
        val snapshot = restored.snapshot() as FiremakingData
        assertEquals(original.duration, snapshot.duration)
        assertEquals(original.zones, snapshot.zones)
        assertEquals(0, snapshot.failures)
        assertEquals(0, snapshot.bankFailures)
    }
}
