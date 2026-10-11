package api.bot.script

import api.bot.zone.SubZone
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Regression coverage for combat search recovery.
 * @author Codex
 */
class TargetSearchRecoveryTest {
    private class SearchScript(bot: Bot, timeout: Duration = 180.seconds) :
        TargetingZonedBotScript<Npc>(bot, 3600.seconds, mutableListOf(SubZone.LUMBRIDGE_RIVER)) {
        override val targetSearchTimeout = timeout
        var now = 0L
        var attempts = 0
        var attemptSeconds = 90L
        var accepted = false
        var candidateCount = 20
        var scanSeconds = 0L
        val target = mock(Npc::class.java).also {
            `when`(it.state).thenReturn(EntityState.ACTIVE)
            `when`(it.position).thenReturn(Position(3248, 3244))
        }

        init { setZone(SubZone.LUMBRIDGE_RIVER) }

        fun setZone(zone: SubZone) {
            ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
                isAccessible = true
                set(this@SearchScript, zone)
            }
        }

        override fun targetSearchTimeNanos() = now
        override suspend fun refocus() = true
        override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
            now += scanSeconds * 1_000_000_000
            return MutableList(candidateCount) { target }
        }
        override suspend fun interactionOption(target: Npc): Int? {
            attempts++
            now += attemptSeconds * 1_000_000_000
            return if (accepted) 3 else null
        }
        override fun snapshot(): BotScriptData? = null
    }

    private fun bot() = mock(Bot::class.java, RETURNS_DEEP_STUBS)

    @Test
    fun rejectsQuietZoneWithinFirstScanEvenWhenItIsTheOnlyZone() = runBlocking {
        val script = SearchScript(bot())
        assertFalse(script.executeInZone())
        assertEquals(2, script.attempts)
        // The base ZonedBotScript removes this rejected zone and ends if no alternatives remain.
    }

    @Test
    fun zonedLifecycleRemovesRejectedZoneAndStopsWhenNoneRemain() = runBlocking {
        val bot = bot()
        `when`(bot.subZones).thenReturn(EnumSet.of(SubZone.LUMBRIDGE_RIVER))
        val script = SearchScript(bot)
        assertFalse(script.init(true))
        assertTrue(script.run())
        assertTrue(script.zones.isEmpty())
    }

    @Test
    fun emptySearchesShareOneBudget() = runBlocking {
        val script = SearchScript(bot())
        script.candidateCount = 0
        script.scanSeconds = 90
        assertTrue(script.executeInZone())
        assertFalse(script.executeInZone())
        assertEquals(0, script.attempts)
    }

    @Test
    fun pauseAndBankingResetSearchBudget() = runBlocking {
        for (banking in listOf(false, true)) {
            val script = SearchScript(bot())
            script.candidateCount = 0
            script.scanSeconds = 90
            assertTrue(script.executeInZone())
            if (banking) assertTrue(script.onBankRequested(false)) else script.onPaused()
            script.now += 3600L * 1_000_000_000
            assertTrue(script.executeInZone())
            assertFalse(script.executeInZone())
        }
    }

    @Test
    fun successfulSelectionStartsFreshBudgetForNextSearch() = runBlocking {
        val bot = bot()
        val script = SearchScript(bot)
        script.accepted = true
        `when`(bot.actionHandler.interactions.interact(3, script.target)).thenReturn(true)
        assertTrue(script.executeInZone())
        script.now += 3600L * 1_000_000_000 // Time spent fighting must not count as searching.
        script.accepted = false
        assertFalse(script.executeInZone())
        assertEquals(3, script.attempts)
    }

    @Test
    fun failedInteractionsAlsoConsumeBudget() = runBlocking {
        val bot = bot()
        val script = SearchScript(bot)
        script.accepted = true
        `when`(bot.actionHandler.interactions.interact(3, script.target)).thenReturn(false)
        assertFalse(script.executeInZone())
        assertEquals(2, script.attempts)
    }

    @Test
    fun rejectedZoneDoesNotPoisonNextZone() = runBlocking {
        val script = SearchScript(bot())
        assertFalse(script.executeInZone())
        script.setZone(SubZone.LUMBRIDGE_CHICKEN_COOP)
        assertFalse(script.executeInZone())
        assertEquals(4, script.attempts)
    }

    @Test
    fun successfulSlowAttemptIsAllowedToFinish() = runBlocking {
        val bot = bot()
        val script = SearchScript(bot)
        script.accepted = true
        script.attemptSeconds = 270
        `when`(bot.actionHandler.interactions.interact(3, script.target)).thenReturn(true)
        assertTrue(script.executeInZone())
        assertEquals(1, script.attempts)
    }

    @Test
    fun scriptsWithoutTimeoutCanStillSelectAfterLongSearch() = runBlocking {
        val bot = bot()
        val script = SearchScript(bot, Duration.INFINITE)
        script.accepted = true
        script.attemptSeconds = 3600
        `when`(bot.actionHandler.interactions.interact(3, script.target)).thenReturn(true)
        assertTrue(script.executeInZone())
    }
}
