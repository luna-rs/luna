package api.bot.action

import api.bot.script.InventoryProductionFixtures
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.withTimeout
import api.predef.world
import io.luna.game.task.Task
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*

/**
 * Verifies the bank handler enforces common safety even when invoked outside a zoned script.
 *
 * @author lare96
 */
class BankActionSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun interrupted(mode: String): Bot = InventoryProductionFixtures.bot().also { bot ->
        `when`(bot.state).thenReturn(EntityState.INACTIVE)
        when (mode) {
            "dead" -> `when`(bot.health).thenReturn(0)
            "locked" -> `when`(bot.isLocked).thenReturn(true)
            "combat" -> `when`(bot.combat.inCombat()).thenReturn(true)
            "strong" -> bot.actions.submit(object : Action<Bot>(bot, ActionType.STRONG) {
                override fun run() = false
            })
        }
    }

    @ParameterizedTest @ValueSource(strings = ["dead", "locked", "combat", "strong"])
    fun depositsDoNotSendInputsAfterRecoveryAborts(mode: String) = runBlocking<Unit> {
        val bot = interrupted(mode)
        val output = bot.output
        val banking = BotBankingActionHandler(bot, bot.actionHandler)
        assertFalse(banking.deposit(Item(995)))
        verifyNoInteractions(output)
    }

    @ParameterizedTest @ValueSource(strings = ["dead", "locked", "combat", "strong"])
    fun withdrawalsDoNotSendInputsAfterRecoveryAborts(mode: String) = runBlocking<Unit> {
        val bot = interrupted(mode)
        val output = bot.output
        val banking = BotBankingActionHandler(bot, bot.actionHandler)
        assertFalse(banking.withdraw(Item(995)))
        verifyNoInteractions(output)
    }

    @ParameterizedTest @ValueSource(strings = ["dead", "locked", "combat", "strong"])
    fun bankingModeCleanupRemainsAvailableWhileUnsafe(mode: String) {
        val bot = interrupted(mode)
        val varps = bot.varpManager
        BotBankingActionHandler(bot, bot.actionHandler).clickBankingMode(false)
        verify(varps).setValue(PersistentVarp.WITHDRAW_AS_NOTE, 0)
    }

    @Test fun travelCannotOpenABankIfTheBotBecomesLockedOnArrival() = runBlocking<Unit> {
        val bot = interrupted("locked")
        val handler = bot.actionHandler
        val interactions = handler.interactions
        val banking = spy(BotBankingActionHandler(bot, handler))
        doReturn(mock(GameObject::class.java)).`when`(banking).travelToNearestBank()
        assertFalse(banking.travelToBankOpen())
        verifyNoInteractions(interactions)
    }

    @Test fun logoutDuringReactionDelayPreventsTheBankClick() = runTest {
        val bot = InventoryProductionFixtures.bot()
        val handler = bot.actionHandler
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            BotBankingActionHandler(bot, handler).open(mock(GameObject::class.java))
        }
        `when`(bot.state).thenReturn(EntityState.INACTIVE)
        assertFalse(withTimeout(5_000) { pending.await() })
        verifyNoInteractions(handler.interactions)
    }

    @Test fun interruptionDuringInteractionPreventsWaitingAndFallback() = runTest {
        val bot = InventoryProductionFixtures.bot()
        val bank = mock(GameObject::class.java)
        val handler = bot.actionHandler
        `when`(handler.interactions.interact(eq(2), same(bank) ?: bank)).thenAnswer {
            `when`(bot.state).thenReturn(EntityState.INACTIVE)
            `when`(bot.combat.inCombat()).thenReturn(true)
            true
        }
        assertFalse(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(bank) })
        assertFalse(bot.bank.isOpen)
    }

    @Test fun logoutDuringInterfaceWaitPreventsDirectOpenFallback() = runTest {
        val bot = InventoryProductionFixtures.bot()
        val bank = mock(GameObject::class.java)
        val handler = bot.actionHandler
        `when`(handler.interactions.interact(eq(2), same(bank) ?: bank)).thenReturn(true)
        val run = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer {
            `when`(bot.state).thenReturn(EntityState.INACTIVE)
            val task = it.getArgument<Task>(0)
            repeat(10) { run.invoke(task) }
            null
        }.`when`(world).schedule(any(Task::class.java))
        try {
            assertFalse(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(bank) })
            assertFalse(bot.bank.isOpen)
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
        }
    }

    @Test fun safeInterfaceTimeoutRetainsTheExistingOpenFallback() = runTest {
        val bot = InventoryProductionFixtures.bot()
        val bank = spy(bot.bank)
        `when`(bot.bank).thenReturn(bank)
        doNothing().`when`(bank).open()
        val target = mock(GameObject::class.java)
        val handler = bot.actionHandler
        `when`(handler.interactions.interact(eq(2), same(target) ?: target)).thenReturn(true)
        val run = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer {
            val task = it.getArgument<Task>(0)
            repeat(10) { run.invoke(task) }
            null
        }.`when`(world).schedule(any(Task::class.java))
        try {
            assertTrue(withTimeout(5_000) { BotBankingActionHandler(bot, handler).open(target) })
            verify(bank).open()
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
        }
    }
}
