package game.bot.scripts.skills

import api.bot.webwalk.plan.BankAccess
import engine.bot.coordinator.skill.ScriptFixtures
import game.skill.crafting.hideTanning.Hide
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.time.Duration.Companion.minutes

class TanningApproachTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() = ScriptFixtures.initialize()
    }
    private lateinit var bot: Bot
    private lateinit var tanner: Npc
    private lateinit var script: TanHideBotScript
    private val tannery = Position(3273, 3191)
    private val nearby = Position(3273, 3190)

    @BeforeEach fun setup() = runBlocking<Unit> {
        bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        tanner = mock(Npc::class.java)
        `when`(bot.health).thenReturn(10)
        `when`(bot.position).thenReturn(Position(3270, 3167))
        `when`(tanner.position).thenReturn(tannery)
        `when`(bot.actionHandler.interactions.interact(2, tanner)).thenReturn(false)
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenReturn(false)
        script = TanHideBotScript(bot, 5.minutes)
    }

    private suspend fun start(): Boolean = suspendCoroutineUninterceptedOrReturn { continuation ->
        try {
            TanHideBotScript::class.java.getDeclaredMethod("startTanning", Hide::class.java,
                Npc::class.java, Continuation::class.java).apply { isAccessible = true }
                .invoke(script, Hide.SOFT_LEATHER, tanner, continuation)
        } catch (error: InvocationTargetException) {
            throw error.cause ?: error
        }
    }

    @Test fun bankToTanneryWalkPrecedesNormalTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer {
            `when`(bot.position).thenReturn(nearby)
            true
        }
        assertFalse(start()) // Native talk is deliberately rejected by the fixture.
        val order = inOrder(bot.actionHandler, bot.actionHandler.interactions)
        order.verify(bot.actionHandler).webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)
        order.verify(bot.actionHandler.interactions).interact(2, tanner)
        verifyNoInteractions(bot.actionHandler.banking)
    }

    @Test fun failedRouteDoesNotAttemptOutOfViewTalk() = runBlocking<Unit> {
        assertFalse(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
        verifyNoInteractions(bot.output)
    }

    @Test fun reportedArrivalStillRequiresCurrentVisibility() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenReturn(true)
        assertFalse(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun tannerMovingAwayDuringTravelPreventsTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer {
            `when`(bot.position).thenReturn(nearby)
            `when`(tanner.position).thenReturn(Position(3300, 3191))
            true
        }
        assertFalse(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun combatStartingDuringTravelPreventsTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer {
            `when`(bot.position).thenReturn(nearby)
            `when`(bot.combat.inCombat()).thenReturn(true)
            true
        }
        assertFalse(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun lockAcquiredDuringTravelPreventsTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer {
            `when`(bot.position).thenReturn(nearby)
            `when`(bot.isLocked).thenReturn(true)
            true
        }
        assertFalse(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun alreadyVisibleTannerUsesExistingInteractionWithoutWebTrip() = runBlocking<Unit> {
        `when`(bot.position).thenReturn(nearby)
        assertFalse(start())
        verify(bot.actionHandler, never()).webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)
        verify(bot.actionHandler.interactions).interact(2, tanner)
    }

    @Test fun cancellationDuringTravelPropagatesWithoutTalk() {
        assertThrows(CancellationException::class.java) {
            runBlocking {
                `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE))
                    .thenAnswer { throw CancellationException("cancel travel") }
                start()
            }
        }
        runBlocking { verify(bot.actionHandler.interactions, never()).interact(anyInt(), any()) }
    }
}
