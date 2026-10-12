package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import game.bot.scripts.skills.FiremakingBotScript.FiremakingLane
import game.bot.scripts.skills.FiremakingBotScript.FiremakingSpot
import io.luna.game.model.EntityState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import game.skill.firemaking.Log
import kotlin.time.Duration.Companion.minutes

/**
 * Verifies Firemaking lifecycle cleanup preserves other sessions' spot claims.
 *
 * @author lare96
 */
class FiremakingCleanupBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private val laneField = FiremakingBotScript::class.java.getDeclaredField("lane").apply { isAccessible = true }

    private fun script(spot: FiremakingSpot): FiremakingBotScript {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        return InventoryProductionFixtures.active(FiremakingBotScript(bot, Log.NORMAL, 8.minutes, mutableListOf(spot.zone)))
    }

    @ParameterizedTest @ValueSource(strings = ["pause", "finish", "zone"])
    fun lifecycleHooksReleaseTheSpotAndTransientLaneWhileTheOldJobStillExists(hook: String) = runBlocking<Unit> {
        val spot = FiremakingSpot.VARROCK_WEST
        val first = script(spot)
        val replacement = script(spot)
        try {
            assertTrue(spot.join(first))
            laneField.set(first, spot.lanes.first())
            assertTrue(spot.isTaken())
            assertFalse(spot.join(replacement))
            when (hook) {
                "pause" -> first.onPaused()
                "finish" -> first.finish()
                "zone" -> first.onNewActiveZone(spot.zone)
            }
            assertNull(laneField.get(first))
            assertFalse(spot.isTaken())
            assertTrue(spot.join(replacement))
            assertTrue(spot.isTaken(first))
        } finally {
            FiremakingSpot.leave(first)
            FiremakingSpot.leave(replacement)
        }
    }

    @Test fun repeatedCleanupDoesNotReleaseAnotherSessionsClaimOrLane() = runBlocking<Unit> {
        val spot = FiremakingSpot.DRAYNOR
        val first = script(spot)
        val replacement = script(spot)
        try {
            assertTrue(spot.join(first))
            first.onPaused()
            assertTrue(spot.join(replacement))
            val lane: FiremakingLane = spot.lanes.first()
            laneField.set(replacement, lane)
            first.onPaused()
            first.finish()
            first.onNewActiveZone(spot.zone)
            assertTrue(spot.isTaken(first))
            assertFalse(spot.isTaken(replacement))
            assertSame(lane, laneField.get(replacement))
        } finally {
            FiremakingSpot.leave(first)
            FiremakingSpot.leave(replacement)
        }
    }
}
