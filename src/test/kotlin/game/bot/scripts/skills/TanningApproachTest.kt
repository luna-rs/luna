package game.bot.scripts.skills

import api.bot.webwalk.plan.BankAccess
import api.bot.script.ZonedBotScript
import api.bot.zone.SubZone
import io.luna.game.model.EntityState
import kotlinx.coroutines.delay
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
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
        `when`(bot.personality.isDextrous).thenReturn(true)
        `when`(bot.inventory.computeAmountForId(1739)).thenReturn(1)
        `when`(bot.inventory.computeAmountForId(995)).thenReturn(2)
        `when`(bot.actionHandler.widgets.clickCloseInterface()).thenReturn(true)
        `when`(tanner.state).thenReturn(EntityState.ACTIVE)
        `when`(tanner.id).thenReturn(2824)
        `when`(bot.position).thenReturn(Position(3270, 3167))
        `when`(tanner.position).thenReturn(tannery)
        `when`(bot.actionHandler.interactions.interact(2, tanner)).thenReturn(false)
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenReturn(false)
        script = TanHideBotScript(bot, 5.minutes)
        TanHideBotScript::class.java.getDeclaredField("tanning").apply {
            isAccessible = true
            set(script, Hide.SOFT_LEATHER)
        }
        ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
            isAccessible = true
            set(script, SubZone.AL_KHARID_BANK)
        }
        @Suppress("UNCHECKED_CAST")
        val cache = TanHideBotScript::class.java.getDeclaredField("tanners").apply {
            isAccessible = true
        }.get(null) as MutableMap<SubZone, Npc>
        cache.clear()
        cache[SubZone.AL_KHARID_BANK] = tanner
    }

    private suspend fun start(): Boolean = suspendCoroutineUninterceptedOrReturn { continuation ->
        try {
            TanHideBotScript::class.java.getDeclaredMethod("onExecuteInZone",
                Continuation::class.java).apply { isAccessible = true }
                .invoke(script, continuation)
        } catch (error: InvocationTargetException) {
            throw error.cause ?: error
        }
    }

    @Test fun bankToTanneryWalkPrecedesNormalTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer {
            `when`(bot.position).thenReturn(nearby)
            true
        }
        assertTrue(start()) // The cycle continues; native talk is deliberately rejected by the fixture.
        val order = inOrder(bot.actionHandler, bot.actionHandler.interactions)
        order.verify(bot.actionHandler).webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)
        order.verify(bot.actionHandler.interactions).interact(2, tanner)
        verifyNoInteractions(bot.actionHandler.banking)
    }

    @Test fun failedRouteDoesNotAttemptOutOfViewTalk() = runBlocking<Unit> {
        assertTrue(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
        verifyNoInteractions(bot.output)
    }

    @Test fun reportedArrivalStillRequiresCurrentVisibility() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenReturn(true)
        assertTrue(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun tannerMovingAwayDuringTravelPreventsTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer {
            `when`(bot.position).thenReturn(nearby)
            `when`(tanner.position).thenReturn(Position(3300, 3191))
            true
        }
        assertTrue(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun combatStartingDuringTravelPreventsTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer {
            `when`(bot.position).thenReturn(nearby)
            `when`(bot.combat.inCombat()).thenReturn(true)
            true
        }
        assertTrue(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun lockAcquiredDuringTravelPreventsTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer {
            `when`(bot.position).thenReturn(nearby)
            `when`(bot.isLocked).thenReturn(true)
            true
        }
        assertTrue(start())
        verify(bot.actionHandler.interactions, never()).interact(anyInt(), any())
    }

    @Test fun alreadyVisibleTannerUsesExistingInteractionWithoutWebTrip() = runBlocking<Unit> {
        `when`(bot.position).thenReturn(nearby)
        assertTrue(start())
        verify(bot.actionHandler, never()).webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)
        verify(bot.actionHandler.interactions).interact(2, tanner)
    }

    @Test fun travelLongerThanProductionStartBudgetStillAttemptsTalk() = runBlocking<Unit> {
        `when`(bot.actionHandler.webWalkTo(tannery, radius = 1, bank = BankAccess.NONE)).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val continuation = invocation.rawArguments.last() as Continuation<Boolean>
            val travel: suspend () -> Boolean = {
                delay(16_000)
                `when`(bot.position).thenReturn(nearby)
                true
            }
            travel.startCoroutineUninterceptedOrReturn(continuation)
        }
        assertTrue(start())
        verify(bot.actionHandler.interactions).interact(2, tanner)
        verifyNoInteractions(bot.actionHandler.banking)
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
