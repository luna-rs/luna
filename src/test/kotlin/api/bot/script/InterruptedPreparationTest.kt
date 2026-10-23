package api.bot.script

import api.bot.zone.SubZone
import engine.bot.gear.BotGearLocator
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.time.Duration.Companion.minutes

class InterruptedPreparationTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private class Activity(bot: Bot, val locator: BotGearLocator) :
        ZonedBotScript(bot, 8.minutes, mutableListOf(SubZone.HOME)) {
        var bankVisits = 0
        var blockBank = false
        override suspend fun equipment() = locator
        override suspend fun executeInZone() = true
        override suspend fun onBankRequested(initial: Boolean) = initial
        override suspend fun onBankOpen(initial: Boolean) {
            bankVisits++
            if (blockBank) awaitCancellation()
        }
        override fun snapshot(): BotScriptData? = null
    }

    @Test fun interruptionDuringGearResumesUnfinishedPreparation() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val locator = mock(BotGearLocator::class.java)
        var blockGear = true
        `when`(locator.locateAndEquip()).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val continuation = invocation.rawArguments.last() as Continuation<Boolean>
            val equip: suspend () -> Boolean = {
                if (blockGear) awaitCancellation()
                true
            }
            equip.startCoroutineUninterceptedOrReturn(continuation)
        }
        `when`(bot.actionHandler.banking.travelToBankDepositAll()).thenReturn(true)
        val script = Activity(bot, locator)
        val first = async(start = CoroutineStart.UNDISPATCHED) { script.init(false) }
        assertFalse(first.isCompleted)
        assertEquals(0, script.bankVisits)
        first.cancelAndJoin()

        blockGear = false
        assertFalse(script.init(true))
        verify(locator, times(2)).locateAndEquip()
        assertEquals(1, script.bankVisits)
    }

    @Test fun interruptionInsideInitialBankHookDoesNotSkipTheHookOnResume() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val locator = mock(BotGearLocator::class.java)
        `when`(locator.locateAndEquip()).thenReturn(true)
        `when`(bot.actionHandler.banking.travelToBankDepositAll()).thenReturn(true)
        val script = Activity(bot, locator).also { it.blockBank = true }
        val first = async(start = CoroutineStart.UNDISPATCHED) { script.init(false) }
        assertEquals(1, script.bankVisits)
        assertFalse(first.isCompleted)
        first.cancelAndJoin()

        script.blockBank = false
        assertFalse(script.init(true))
        assertEquals(2, script.bankVisits)
        verify(locator, times(2)).locateAndEquip()
    }

    @Test fun completedPreparationIsNotRepeatedOnOrdinaryResume() = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        val locator = mock(BotGearLocator::class.java)
        `when`(locator.locateAndEquip()).thenReturn(true)
        `when`(bot.actionHandler.banking.travelToBankDepositAll()).thenReturn(true)
        val script = Activity(bot, locator)
        assertFalse(script.init(false))
        assertFalse(script.init(true))
        verify(locator, times(1)).locateAndEquip()
        assertEquals(1, script.bankVisits)
    }
}
