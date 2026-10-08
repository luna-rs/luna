package api.bot.script

import api.predef.gameService
import io.luna.LunaContext
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import io.luna.game.plugin.PluginBootstrap
import kotlinx.coroutines.awaitCancellation
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import java.util.ArrayDeque
import java.util.concurrent.CancellationException
import java.util.concurrent.Executor

class ScriptPauseResumeTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initializeBindings() {
            PluginBootstrap::class.java.getDeclaredMethod("setBindings", LunaContext::class.java).apply {
                isAccessible = true
                invoke(null, mock(LunaContext::class.java, RETURNS_DEEP_STUBS))
            }
        }
    }

    private val queue = ArrayDeque<Runnable>()

    @BeforeEach
    fun useQueuedGameExecutor() {
        `when`(gameService.gameExecutor).thenReturn(Executor { queue.addLast(it) })
    }

    private class FixtureScript(bot: Bot, val mode: String) : BotScript(bot) {
        val resumedFlags = mutableListOf<Boolean>()
        var finished = 0
        var completions = 0
        private var resumed = false
        override suspend fun init(resumed: Boolean): Boolean {
            this.resumed = resumed
            resumedFlags.add(resumed)
            if (mode == "unexpectedCancel") throw CancellationException("fixture cancellation")
            if (mode == "exception") throw IllegalStateException("fixture exception")
            if (mode == "init" && !resumed) awaitCancellation()
            return false
        }
        override suspend fun run(): Boolean {
            if (mode == "run" && !resumed) awaitCancellation()
            return true
        }
        override suspend fun finish() { finished++ }
        override suspend fun completed() { completions++ }
        override fun snapshot(): BotScriptData? = null
    }

    private fun fixture(mode: String): FixtureScript {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        return FixtureScript(bot, mode)
    }

    private fun drain() {
        var executed = 0
        while (queue.isNotEmpty()) {
            assertTrue(executed++ < 30, "Unexpected unbounded coroutine dispatch")
            queue.removeFirst().run()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["init", "run"])
    fun pausedScriptCanResumeAfterCancellationCleanup(mode: String) {
        val script = fixture(mode)
        assertTrue(script.start())
        drain()
        assertTrue(script.pause())
        drain()
        assertTrue(script.isPaused())
        assertEquals(1, script.finished)
        assertEquals(0, script.completions)
        assertTrue(script.start())
        drain()
        assertEquals(listOf(false, true), script.resumedFlags)
        assertEquals(2, script.finished)
        assertEquals(1, script.completions)
        assertFalse(script.start())
    }

    @Test
    fun oldCancellationCleanupDoesNotStopAnAlreadyResumedJob() {
        val script = fixture("init")
        assertTrue(script.start())
        drain()
        assertTrue(script.pause())
        // Queue the resumed job before processing the old job's cancellation cleanup.
        assertTrue(script.start())
        drain()
        assertEquals(listOf(false, true), script.resumedFlags)
        assertEquals(2, script.finished)
        assertEquals(1, script.completions)
    }

    @Test
    fun explicitStopRemainsPermanent() {
        val script = fixture("init")
        assertTrue(script.start())
        drain()
        assertTrue(script.stop())
        drain()
        assertFalse(script.isPaused())
        assertFalse(script.start())
        assertEquals(1, script.finished)
        assertEquals(0, script.completions)
    }

    @ParameterizedTest
    @ValueSource(strings = ["unexpectedCancel", "exception"])
    fun failuresRemainPermanent(mode: String) {
        val script = fixture(mode)
        assertTrue(script.start())
        drain()
        assertFalse(script.start())
        assertEquals(1, script.finished)
        assertEquals(0, script.completions)
    }
}
