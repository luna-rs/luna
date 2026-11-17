package engine.bot.coordinator.skill

import api.predef.rand
import io.luna.game.model.mob.bot.Bot
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Samples varied work sessions, with most visits staying between twenty and forty-five minutes.
 * Dexterity and skill affinity smoothly favor longer sessions while preserving short visits.
 * Only preferred skills can receive rare sessions lasting 90 to 120 minutes.
 * The owner retains this one sampled duration through batches, levels and pauses.
 *
 * @author lare96
 */
internal object SkillingSessionDuration {
    /**
     * Chooses one duration for the actual selected skill, before its first native batch is planned.
     */
    fun sample(bot: Bot, skill: Int? = null): Duration = sampleMinutes(bot.personality.dexterity,
        skill != null && skill in bot.preferences.skills, rand(0.0, 1.0), rand(0.0, 1.0)).minutes

    /**
     * Resolves independent band and minute rolls without a sharp personality threshold or a duration multiplier.
     * At neutral dexterity the four chances for a preferred skill are 20%, 55%, 23% and 2%.
     */
    internal fun sampleMinutes(dexterity: Double, preferred: Boolean, bandRoll: Double, minuteRoll: Double): Int {
        require(dexterity.isFinite() && bandRoll.isFinite() && minuteRoll.isFinite())
        val focus = dexterity.coerceIn(0.0, 1.0)
        val short = 0.31 - focus * 0.12 - if (preferred) 0.05 else 0.0
        val ordinary = 0.55
        val extended = if (preferred) 0.01 + focus * 0.02 else 0.0
        val roll = bandRoll.coerceIn(0.0, 1.0)
        val band = when {
            roll < short -> 10..20
            roll < short + ordinary -> 20..45
            !preferred || roll < 1.0 - extended -> 45..90
            else -> 90..120
        }
        val width = band.last - band.first + 1
        return band.first + floor(minuteRoll.coerceIn(0.0, 1.0) * width).toInt().coerceAtMost(width - 1)
    }
}
