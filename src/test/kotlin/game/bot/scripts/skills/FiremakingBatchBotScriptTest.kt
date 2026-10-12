package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import engine.bot.coordinator.skill.FiremakingScriptFactory
import game.skill.firemaking.Firemaking.TINDERBOX
import game.skill.firemaking.Log
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies partial Firemaking batches and inherited withdrawal handling.
 *
 * @author lare96
 */
class FiremakingBatchBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.firemaking.staticLevel).thenReturn(99)
        `when`(it.personality.isDextrous).thenReturn(true)
        `when`(it.position).thenReturn(Position(3200, 3430))
    }

    private fun script(bot: Bot) = InventoryProductionFixtures.active(
        FiremakingBotScript(bot, Log.NORMAL, 8.minutes, mutableListOf(api.bot.zone.SubZone.HOME)))

    @ParameterizedTest @ValueSource(ints = [1, 8, 27, 50])
    fun factorySelectsAndStartupSizesPartialOrFullBankStock(stock: Int) {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Log.NORMAL.id, stock))
        val selected = FiremakingScriptFactory.getBurnScript(bot)
        assertNotNull(selected)
        assertEquals(Log.NORMAL, selected!!.log)
        val script = InventoryProductionFixtures.active(selected)
        assertEquals(listOf(Item(TINDERBOX), Item(Log.NORMAL.id, minOf(stock, 27))), script.withdraw())
        assertFalse(script.isTerminated())
    }

    @Test fun startupCountsInventoryButBankBatchCountsOnlyDepositedStock() {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(Log.NORMAL.id, 4), Item(TINDERBOX))
        InventoryProductionFixtures.bank(bot, Item(Log.NORMAL.id, 2))
        val script = script(bot)
        assertEquals(listOf(Item(TINDERBOX), Item(Log.NORMAL.id, 6)), script.withdraw())
        assertEquals(listOf(Item(TINDERBOX), Item(Log.NORMAL.id, 2)), FiremakingBotScript::class.java.getDeclaredMethod("bankWithdraw").apply { isAccessible = true }.invoke(script))
    }

    @Test fun factoryRejectsLogsAboveItsLevelAndEmptyStock() {
        val bot = bot()
        assertNull(FiremakingScriptFactory.getBurnScript(bot, 1))
        InventoryProductionFixtures.bank(bot, Item(Log.OAK.id, 3))
        assertNull(FiremakingScriptFactory.getBurnScript(bot, 1))
    }

    @Test fun actualPartialBankTransferIsUnnotedAndUsable() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Log.NORMAL.id, 3), Item(TINDERBOX))
        val script = script(bot)
        val batch = listOf(Item(TINDERBOX), Item(Log.NORMAL.id, 3))
        val banking = bot.actionHandler.banking
        doAnswer {
            batch.forEach { item -> assertTrue(bot.bank.remove(item)); assertTrue(bot.inventory.add(item)) }
            true
        }.`when`(banking).withdrawAll(batch)
        script.onBankOpen(true)
        assertFalse(script.isTerminated())
        assertEquals(3, bot.inventory.computeAmountForId(Log.NORMAL.id))
        assertTrue(bot.inventory.contains(TINDERBOX))
        verify(banking).clickBankingMode(false)
    }

    @Test fun optimisticWithdrawalWithoutLogsStopsInsteadOfStartingALane() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Log.NORMAL.id, 2), Item(TINDERBOX))
        val script = script(bot)
        val banking = bot.actionHandler.banking
        `when`(banking.withdrawAll(listOf(Item(TINDERBOX), Item(Log.NORMAL.id, 2)))).thenReturn(true)
        script.onBankOpen(true)
        assertTrue(script.isTerminated())
        assertFalse(bot.inventory.contains(Log.NORMAL.id))
    }

    @Test fun remainingBankLogsRequestBankingInsteadOfWoodcutting() = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.bank(bot, Item(Log.NORMAL.id, 1), Item(TINDERBOX))
        val script = script(bot)
        assertTrue(script.executeInZone())
        assertTrue(script.onBankRequested(false))
        assertFalse(script.isTerminated())
        verifyNoInteractions(bot.scriptStack)
    }
}
