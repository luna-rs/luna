package api.bot.action.trading

import api.bot.action.BotTradingActionHandler
import api.bot.script.InventoryProductionFixtures
import api.predef.world
import engine.trade.OfferTradeInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption.TRADE
import io.luna.game.model.mob.bot.Bot
import io.luna.game.task.Task
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Exercises request interruption before approach and while waiting, preserving established trade screens.
 *
 * @author lare96
 */
class BotTradeRequestInterruptionTest {

    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()

        private fun bot(): Bot = InventoryProductionFixtures.bot().also {
            `when`(it.overlays.player).thenReturn(it)
            `when`(it.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(null)
        }

        private fun open(bot: Bot, other: Player) {
            val screen = mock(OfferTradeInterface::class.java)
            `when`(screen.other).thenReturn(other)
            `when`(bot.overlays.getOverlay(OfferTradeInterface::class.java)).thenReturn(screen)
        }
    }

    @Test fun aPriorityRequestPreventsAnUnansweredApproach(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)

        assertNull(BotTradeRequestStage(bot, other).sendAndAwait { false })
        verifyNoInteractions(bot.actionHandler.trading)
    }

    @Test fun anExistingMatchingTradeWinsOverInterruption(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        open(bot, other)

        assertSame(other, BotTradeRequestStage(bot, other).sendAndAwait { false }?.other)
        verifyNoInteractions(bot.actionHandler.trading)
    }

    @Test fun interruptionDuringApproachPreventsTheTradeClick(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        val handler = bot.actionHandler
        `when`(bot.contextMenu.contains(TRADE)).thenReturn(true)
        `when`(handler.trading).thenReturn(BotTradingActionHandler(bot, handler))
        var keepGoing = true
        `when`(handler.interactions.interact(eq(TRADE.index), same(other) ?: other,
            any<() -> Boolean>() ?: { true })).thenAnswer {
            keepGoing = false
            it.getArgument<() -> Boolean>(2).invoke()
        }

        assertNull(BotTradeRequestStage(bot, other).sendAndAwait { keepGoing })
        verifyNoInteractions(bot.output)
    }

    @Test fun aTradeOpenedDuringApproachWinsEvenIfApproachReportsFailure(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        var keepGoing = true
        `when`(bot.actionHandler.trading.sendTradeRequest(same(other) ?: other,
            any<() -> Boolean>() ?: { true })).thenAnswer {
            keepGoing = false
            open(bot, other)
            false
        }

        assertSame(other, BotTradeRequestStage(bot, other).sendAndAwait { keepGoing }?.other)
    }

    @Test fun anUnansweredRequestYieldsOnTheNextConditionTick(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        var keepGoing = true
        `when`(bot.actionHandler.trading.sendTradeRequest(same(other) ?: other,
            any<() -> Boolean>() ?: { true })).thenReturn(true)
        val run = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer {
            keepGoing = false
            run.invoke(it.getArgument<Task>(0))
            null
        }.`when`(world).schedule(any(Task::class.java))
        try {
            assertNull(withTimeout(5_000) { BotTradeRequestStage(bot, other).sendAndAwait { keepGoing } })
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
        }
    }

    @Test fun aMatchingTradeOpenedOnTheInterruptionTickIsPreserved(): Unit = runBlocking {
        val bot = bot()
        val other = mock(Player::class.java)
        var keepGoing = true
        `when`(bot.actionHandler.trading.sendTradeRequest(same(other) ?: other,
            any<() -> Boolean>() ?: { true })).thenReturn(true)
        val run = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer {
            keepGoing = false
            open(bot, other)
            run.invoke(it.getArgument<Task>(0))
            null
        }.`when`(world).schedule(any(Task::class.java))
        try {
            assertSame(other, withTimeout(5_000) {
                BotTradeRequestStage(bot, other).sendAndAwait { keepGoing }
            }?.other)
        } finally {
            doNothing().`when`(world).schedule(any(Task::class.java))
        }
    }
}
