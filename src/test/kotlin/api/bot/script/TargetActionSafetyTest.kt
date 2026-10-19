package api.bot.script

import api.bot.zone.SubZone
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises interruptions at each shared target-selection boundary without losing the work zone or candidate.
 *
 * @author lare96
 */
class TargetActionSafetyTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    /**
     * A target activity whose hooks simulate an intervening native action while suspended.
     *
     * @author lare96
     */
    private class Activity(bot: Bot, var interruptAt: String) :
        TargetingZonedBotScript<GameObject>(bot, 8.minutes, mutableListOf(SubZone.AL_KHARID_BANK)) {
        var scans = 0
        var now = 0L
        val target = mock(GameObject::class.java).also {
            `when`(it.state).thenReturn(EntityState.ACTIVE)
            `when`(it.position).thenReturn(SubZone.AL_KHARID_BANK.inside)
        }
        override val targetSearchTimeout = 10.seconds
        init {
            ZonedBotScript::class.java.getDeclaredField("activeZone").apply {
                isAccessible = true
                set(this@Activity, SubZone.AL_KHARID_BANK)
            }
        }
        fun interrupt(stage: String) {
            if (interruptAt == stage) `when`(bot.isLocked).thenReturn(true)
        }
        fun cachedTargets() = lastOptions.size
        override fun targetSearchTimeNanos() = now
        override suspend fun refocus(): Boolean { interrupt("refocus"); return true }
        override suspend fun onExecuteInZone(searching: Boolean) { interrupt("hook") }
        override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
            scans++
            interrupt("find")
            return mutableListOf(target)
        }
        override suspend fun onAssignFocus(newFocus: GameObject): Boolean { interrupt("assign"); return true }
        override suspend fun interactionOption(target: GameObject): Int? { interrupt("option"); return 1 }
        override fun snapshot(): BotScriptData? = null
    }

    @ParameterizedTest
    @ValueSource(strings = ["refocus", "hook", "find", "assign", "option", "interaction"])
    fun interruptedSelectionDefersAndResumesWithoutAbandoningTheZone(stage: String) = runBlocking<Unit> {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.position).thenReturn(SubZone.AL_KHARID_BANK.inside)
        val script = Activity(bot, stage)
        val interactions = bot.actionHandler.interactions
        `when`(interactions.interact(1, script.target)).thenAnswer {
            script.interrupt("interaction")
            true
        }
        assertTrue(script.executeInZone())
        if (stage !in setOf("refocus", "hook")) assertEquals(1, script.cachedTargets())
        if (stage != "interaction") verify(interactions, never()).interact(1, script.target)
        assertEquals(listOf(SubZone.AL_KHARID_BANK), script.zones)
        // Time spent locked must not expire the ten-second target-search budget.
        script.now = 1_000_000_000_000L
        script.interruptAt = ""
        `when`(bot.isLocked).thenReturn(false)
        assertTrue(script.executeInZone())
        assertEquals(1, script.scans)
        verify(interactions, times(if (stage == "interaction") 2 else 1)).interact(1, script.target)
    }
}
