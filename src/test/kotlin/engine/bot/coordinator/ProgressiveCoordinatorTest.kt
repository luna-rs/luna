package engine.bot.coordinator

import api.bot.script.*
import api.predef.gameService
import api.predef.world
import com.google.gson.JsonObject
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.mob.bot.script.BotScriptStack
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import java.util.ArrayDeque
import java.util.concurrent.Executor

/**
 * Exercises the real script stack across skipped, successful, failed and interrupted preliminary stages.
 *
 * @author lare96
 */
class ProgressiveCoordinatorTest {
    private lateinit var bot: Bot
    private lateinit var stack: BotScriptStack
    private val queue = ArrayDeque<Runnable>()

    @BeforeEach fun setup() {
        InventoryProductionFixtures.initialize()
        bot = InventoryProductionFixtures.bot()
        stack = BotScriptStack(bot, BotScriptManager())
        `when`(bot.scriptStack).thenReturn(stack)
        `when`(gameService.gameExecutor).thenReturn(Executor { queue.addLast(it) })
        doAnswer { queue.addLast(it.getArgument<Runnable>(0)); null }
            .`when`(gameService).yield(any(Runnable::class.java))
    }

    @AfterEach fun cleanup() {
        stack.current()?.takeIf { it.isRunning() }?.stop()
        drain()
        doNothing().`when`(gameService).yield(any(Runnable::class.java))
    }

    private fun drain() {
        var count = 0
        while (queue.isNotEmpty()) {
            check(count++ < 50)
            queue.removeFirst().run()
        }
    }

    private fun tick() { stack.process(); drain() }

    /**
     * Configurable activity with real lifecycle cancellation and failure handling.
     *
     * @author lare96
     */
    private class Stage(bot: Bot, val body: suspend () -> Unit) : DynamicBotScript(bot) {
        override suspend fun run(): Boolean { body(); return true }
    }

    /**
     * Records deferred main selection without running game travel.
     *
     * @author lare96
     */
    private class Coordinator(val first: BotScript?, val main: BotScript) : ProgressiveCoordinator() {
        override val activity get() = BotActivity.SOCIALIZING
        var selections = 0
        override fun firstScript(bot: Bot) = first
        override fun nextScript(bot: Bot): BotScript { selections++; return main }
    }

    @Test fun skippedPreliminaryStillSelectsMain() {
        val main = Stage(bot) {}
        val coordinator = Coordinator(null, main)
        coordinator.accept(bot)
        assertEquals(0, coordinator.selections)
        tick(); tick()
        assertEquals(1, coordinator.selections)
        assertSame(main, stack.current())
    }

    @Test fun successfulPreliminaryChangesStateBeforeMainSelection() {
        var donated = false
        val first = Stage(bot) { donated = true }
        val coordinator = Coordinator(first, Stage(bot) { assertTrue(donated) })
        coordinator.accept(bot)
        assertEquals(2, stack.size())
        tick(); tick()
        assertTrue(donated)
        assertEquals(0, coordinator.selections)
        tick(); tick(); tick()
        assertEquals(1, coordinator.selections)
    }

    @Test fun exceptionInPreliminaryDoesNotDiscardMain() {
        val coordinator = Coordinator(Stage(bot) { error("fixture failure") }, Stage(bot) {})
        coordinator.accept(bot)
        repeat(4) { tick() }
        assertEquals(1, coordinator.selections)
    }

    @Test fun stoppedPreliminaryAdvancesButPauseRetainsBothStages() {
        val first = Stage(bot) { awaitCancellation() }
        val coordinator = Coordinator(first, Stage(bot) {})
        coordinator.accept(bot)
        tick()
        assertTrue(first.pause())
        drain()
        assertEquals(2, stack.size())
        assertEquals(0, coordinator.selections)
        tick()
        assertTrue(first.stop())
        drain()
        repeat(3) { tick() }
        assertEquals(1, coordinator.selections)
    }

    @Test fun alreadyDispatchedSavedContinuationCannotQueueMainAgain() = runBlocking<Unit> {
        val data = ProgressiveCoordinator.Continuation.ContinuationData().also { it.dispatched = true }
        val json = JsonObject().also { data.save(it) }
        val restored = ProgressiveCoordinator.Continuation.ContinuationData().also { it.load(json) }
        val continuation = ProgressiveCoordinator.Continuation(bot, restored)
        assertTrue(continuation.run())
        assertEquals(0, stack.size())
    }

    @Test fun restoredContinuationResolvesItsOriginalCoordinator() = runBlocking<Unit> {
        val data = ProgressiveCoordinator.Continuation.ContinuationData()
        val json = JsonObject().also { data.save(it) }
        val restored = ProgressiveCoordinator.Continuation.ContinuationData().also { it.load(json) }
        val continuation = ProgressiveCoordinator.Continuation(bot, restored)
        stack.push(continuation)
        assertTrue(continuation.run())
        assertTrue(continuation.snapshot().dispatched)
        assertEquals(2, stack.size())
        assertSame(SocializingCoordinator, BotActivity.SOCIALIZING.coordinator)
        assertSame(MinigameCoordinator, BotActivity.MINIGAMES.coordinator)
    }
    @Test fun centralRegistrationRestoresTheWholeProgressiveStackIncludingNestedContinuation() {
        val previous = world.botManager.scriptManager
        val registry = BotScriptManager()
        `when`(world.botManager.scriptManager).thenReturn(registry)
        try {
            InventoryProductionFixtures.register("Scripts")
            stack.push(game.bot.scripts.DepositPartyRoomItemBotScript(bot, 526, 8, 123L))
            stack.push(ProgressiveCoordinator.Continuation(bot, BotActivity.MINIGAMES))
            val saved = stack.save()
            val restored = BotScriptStack(bot, registry)
            restored.load(saved)
            assertEquals(2, restored.size())
            assertInstanceOf(game.bot.scripts.DepositPartyRoomItemBotScript::class.java, restored.current())
            assertEquals(saved.map { it.scriptClass }, restored.save().map { it.scriptClass })
        } finally {
            `when`(world.botManager.scriptManager).thenReturn(previous)
        }
    }

}
