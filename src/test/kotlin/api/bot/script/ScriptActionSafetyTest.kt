package api.bot.script

import api.bot.script.BotScript.ActionSafety
import api.predef.gameService
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import java.util.ArrayDeque
import java.util.concurrent.Executor

/**
 * Verifies common entry conditions, cooperative deferral, combat opt-ins and cancellation cleanup.
 *
 * @author lare96
 */
class ScriptActionSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private lateinit var bot: Bot
    private val queue = ArrayDeque<Runnable>()
    private val scripts = mutableListOf<Activity>()

    @BeforeEach fun setup() {
        bot = InventoryProductionFixtures.bot()
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        `when`(gameService.gameExecutor).thenReturn(Executor { queue.addLast(it) })
        doAnswer { queue.addLast(it.getArgument<Runnable>(0)); null }
            .`when`(gameService).yield(any(Runnable::class.java))
    }

    @AfterEach fun cleanup() {
        scripts.forEach { if (it.isRunning()) it.stop() }
        drain()
        doNothing().`when`(gameService).yield(any(Runnable::class.java))
    }

    /**
     * Minimal normal activity using the real coroutine start, pause and completion paths.
     *
     * @author lare96
     */
    private class Activity(bot: Bot, override val actionSafety: ActionSafety, val holdInit: Boolean) : BotScript(bot) {
        val starts = mutableListOf<Boolean>()
        var runs = 0
        var cleanups = 0
        var completions = 0
        override suspend fun init(resumed: Boolean): Boolean {
            starts += resumed
            if (holdInit) awaitCancellation()
            return false
        }
        override suspend fun run(): Boolean { runs++; return true }
        override suspend fun finish() { cleanups++ }
        override suspend fun completed() { completions++ }
        override fun snapshot(): BotScriptData? = null
    }

    private fun activity(policy: ActionSafety = ActionSafety.NON_COMBAT, holdInit: Boolean = false) =
        Activity(bot, policy, holdInit).also { scripts += it }

    private fun drain() {
        var count = 0
        while (queue.isNotEmpty()) {
            check(count++ < 40) { "Unexpected unbounded coroutine dispatch" }
            queue.removeFirst().run()
        }
    }

    @ParameterizedTest @EnumSource(ActionSafety::class)
    fun healthyIdleBotCanAct(policy: ActionSafety) {
        assertTrue(policy.allows(bot))
    }

    @ParameterizedTest @EnumSource(ActionSafety::class)
    fun combatRequiresExplicitPermission(policy: ActionSafety) {
        `when`(bot.combat.inCombat()).thenReturn(true)
        assertEquals(policy != ActionSafety.NON_COMBAT, policy.allows(bot))
    }

    @ParameterizedTest @EnumSource(ActionSafety::class)
    fun deathDefersActivitiesButLeavesLifecycleOperationsAvailable(policy: ActionSafety) {
        `when`(bot.health).thenReturn(0)
        assertEquals(policy == ActionSafety.NONE, policy.allows(bot))
    }

    @ParameterizedTest @EnumSource(ActionSafety::class)
    fun lockDefersActivitiesWithoutUnlockingTheBot(policy: ActionSafety) {
        `when`(bot.isLocked).thenReturn(true)
        assertEquals(policy == ActionSafety.NONE, policy.allows(bot))
        verify(bot, never()).unlock()
    }

    @ParameterizedTest @EnumSource(ActionSafety::class)
    fun strongActionDefersActivitiesWithoutInterruptingIt(policy: ActionSafety) {
        val action = object : Action<Bot>(bot, ActionType.STRONG) {
            override fun run() = false
        }
        bot.actions.submit(action)
        assertEquals(policy == ActionSafety.NONE, policy.allows(bot))
        assertEquals(1, bot.actions.size(ActionType.STRONG))
    }

    @ParameterizedTest @EnumSource(ActionSafety::class)
    fun weakActionIsOwnedByTheActivityRatherThanTheEntryPolicy(policy: ActionSafety) {
        bot.actions.submit(object : Action<Bot>(bot, ActionType.WEAK) {
            override fun run() = false
        })
        assertTrue(policy.allows(bot))
    }

    @Test fun unsafeStartupCanPauseAndResumeWithoutRunningInitializationEarly() {
        `when`(bot.isLocked).thenReturn(true)
        val script = activity()
        assertTrue(script.start())
        drain()
        assertTrue(script.starts.isEmpty())
        assertTrue(script.pause())
        drain()
        assertEquals(1, script.cleanups)
        assertEquals(0, script.completions)
        `when`(bot.isLocked).thenReturn(false)
        assertTrue(script.start())
        drain()
        assertEquals(listOf(true), script.starts)
        assertEquals(1, script.runs)
        assertEquals(2, script.cleanups)
        assertEquals(1, script.completions)
    }

    @Test fun stateIsRecheckedAfterYieldBeforeRunningActivity() {
        val script = activity()
        doAnswer {
            `when`(bot.health).thenReturn(0)
            queue.addLast(it.getArgument<Runnable>(0))
            null
        }.`when`(gameService).yield(any(Runnable::class.java))
        assertTrue(script.start())
        drain()
        assertEquals(listOf(false), script.starts)
        assertEquals(0, script.runs)
        assertTrue(script.pause())
        drain()
        assertEquals(1, script.cleanups)
    }

    @Test fun cleanupStillRunsWhenPausedDuringDeath() {
        val script = activity(holdInit = true)
        assertTrue(script.start())
        drain()
        `when`(bot.health).thenReturn(0)
        assertTrue(script.pause())
        drain()
        assertEquals(1, script.cleanups)
        assertEquals(0, script.completions)
    }

    @Test fun combatActivityContinuesWhileFighting() {
        `when`(bot.combat.inCombat()).thenReturn(true)
        val script = activity(ActionSafety.COMBAT)
        assertTrue(script.start())
        drain()
        assertEquals(1, script.runs)
        assertEquals(1, script.completions)
    }

    @Test fun deferredActionContinuesAfterNativeStateSettles() = runBlocking<Unit> {
        `when`(bot.isLocked).thenReturn(true)
        val pending = async(start = CoroutineStart.UNDISPATCHED) { ActionSafety.NON_COMBAT.await(bot) }
        assertFalse(pending.isCompleted)
        `when`(bot.isLocked).thenReturn(false)
        assertTrue(withTimeout(4_000) { pending.await() })
    }

    @Test fun inactiveBotCannotStartADeferredActionEvenWhenOtherwiseHealthy() = runBlocking<Unit> {
        `when`(bot.state).thenReturn(EntityState.INACTIVE)
        assertFalse(ActionSafety.NON_COMBAT.await(bot))
    }

    @Test fun emergencyReflexStillRunsDuringDeathAndLocks() {
        `when`(bot.health).thenReturn(0)
        `when`(bot.isLocked).thenReturn(true)
        val reflex = object : ReflexBotScript(bot) {
            var reactions = 0
            override fun shouldReact() = true
            override suspend fun run(): Boolean { reactions++; return true }
        }
        `when`(bot.reflex.activeReflex).thenReturn(reflex)
        reflex.start()
        drain()
        assertEquals(1, reflex.reactions)
    }

    @Test fun deferredActionAbortsWhenBotLeavesWorld() = runBlocking<Unit> {
        `when`(bot.isLocked).thenReturn(true)
        `when`(bot.state).thenReturn(EntityState.INACTIVE)
        assertFalse(ActionSafety.NON_COMBAT.await(bot))
    }
}
