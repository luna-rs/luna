package engine.bot.coordinator

import com.google.gson.JsonObject
import game.bot.scripts.combat.PkBotScript
import game.bot.scripts.combat.PkBotScript.Companion.PkData
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain
import io.luna.game.model.mob.bot.brain.BotPkPreference
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises PK activity priority, bounded personality-based outings and restoration of remaining session time.
 *
 * @author lare96
 */
class PkingCoordinatorTest {
    private fun bot(affinity: Double = 0.5, confidence: Double = 0.5, dedicated: Boolean = false): Bot {
        val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
        `when`(bot.attributes()).thenReturn(AttributeMap())
        `when`(bot.timePlayed).thenReturn(Duration.ofDays(3))
        `when`(bot.preferences.activities).thenReturn(mutableMapOf(BotActivity.PKING to affinity))
        `when`(bot.preferences.pk).thenReturn(BotPkPreference(BotPkPreference.Purpose.VALUE,
            BotPkPreference.Styles.MELEE, BotPkPreference.Build.MAIN))
        `when`(bot.preferences.canChooseActivity(BotActivity.PKING)).thenReturn(true)
        `when`(bot.preferences.isPker).thenReturn(dedicated)
        `when`(bot.personality.confidence).thenReturn(confidence)
        return bot
    }

    @Test fun dedicatedLegacyPkersReceivePriorityWithoutRewritingTheirSavedWeight() {
        val bot = bot(0.04, dedicated = true)
        assertEquals(3.0, PkingCoordinator.activityWeight(bot))
        assertEquals(0.04, bot.preferences.activities[BotActivity.PKING])
        assertSame(PkingCoordinator, BotBrain().process(bot))
        assertEquals(4.0, PkingCoordinator.activityWeight(bot(1.0, dedicated = true)))
        assertEquals(1.5, PkingCoordinator.activityWeight(bot(0.5)))
    }

    @Test fun activityBoostPreservesOptOutAndEligibilityRestrictions() {
        for (weight in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(0.0, PkingCoordinator.activityWeight(bot(weight, dedicated = true)))
        }
        val bot = bot(1.0, dedicated = true)
        `when`(bot.preferences.isSkiller).thenReturn(true)
        assertEquals(0.0, PkingCoordinator.activityWeight(bot))
        `when`(bot.preferences.isSkiller).thenReturn(false)
        `when`(bot.preferences.canChooseActivity(BotActivity.PKING)).thenReturn(false)
        assertEquals(0.0, PkingCoordinator.activityWeight(bot))
        `when`(bot.preferences.canChooseActivity(BotActivity.PKING)).thenReturn(true)
        `when`(bot.preferences.pk).thenReturn(null)
        assertEquals(0.0, PkingCoordinator.activityWeight(bot))
        bot.preferences.activities[BotActivity.TRAINING_SKILLS] = 1.0
        `when`(bot.preferences.canChooseActivity(BotActivity.TRAINING_SKILLS)).thenReturn(true)
        assertSame(BotActivity.TRAINING_SKILLS.coordinator, BotBrain().process(bot))
    }

    @Test fun durationsSpanOneToEightHoursAndIncreaseWithAffinityConfidenceAndDedication() {
        assertEquals(1.hours, PkingCoordinator.sessionDuration(bot(0.0, 0.0)))
        assertEquals(8.hours, PkingCoordinator.sessionDuration(bot(1.0, 1.0, true)))
        val ordinary = PkingCoordinator.sessionDuration(bot(0.5, 0.5))
        assertTrue(PkingCoordinator.sessionDuration(bot(0.75, 0.5)) > ordinary)
        assertTrue(PkingCoordinator.sessionDuration(bot(0.5, 0.75)) > ordinary)
        assertTrue(PkingCoordinator.sessionDuration(bot(0.5, 0.5, true)) > ordinary)
        for (affinity in listOf(-10.0, 0.0, 0.5, 1.0, 10.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            for (confidence in listOf(-10.0, 0.0, 0.5, 1.0, 10.0, Double.NaN)) {
                for (dedicated in listOf(false, true)) {
                    val duration = PkingCoordinator.sessionDuration(bot(affinity, confidence, dedicated))
                    assertTrue(duration in 1.hours..8.hours)
                }
            }
        }
    }

    @Test fun anAllZeroFallbackCannotSelectAnOptedOutPkSession() {
        val bot = bot(0.0, dedicated = true)
        `when`(bot.preferences.canChooseActivity(BotActivity.TRAINING_SKILLS)).thenReturn(true)
        assertSame(BotActivity.TRAINING_SKILLS.coordinator, BotBrain().process(bot))
    }

    @Test fun restoringAnAlmostFinishedOutingDoesNotRenewItForAnotherHour() {
        val bot = bot()
        val saved = PkData().apply { duration = 7.minutes }
        val json = JsonObject().also { saved.save(it) }
        val loaded = PkData().apply { load(json) }
        val restored = PkBotScript(bot, loaded)
        assertEquals(7.minutes, restored.duration)
        assertTrue(restored.snapshot().duration <= 7.minutes)
    }
}
