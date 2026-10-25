package game.skill.farming

import game.skill.farming.patch.*

/**
 * Brings farming patches up to date with the time that has passed since they were last updated, whether the player
 * was online or not.
 *
 * Growth happens at shared wall-clock boundaries: every multiple of [WEED_CYCLE_MINUTES] for weeds on an empty patch,
 * and every multiple of [FarmingPatch.cycleMinutes] for a planted crop.
 *
 * @author hydrozoa
 */
object GrowthEngine {

    /**
     * How many minutes it takes for one weed to grow back on an empty patch.
     */
    const val WEED_CYCLE_MINUTES = 5

    /**
     * Runs every weed or growth cycle of [patch] whose boundary has passed since it was last updated, then marks it
     * as updated at [now], in epoch milliseconds. Growth stops early once the crop is fully grown or dead. The patch's
     * other timers are brought up to date first, see [FarmingPatch.advanceTimers].
     *
     * @return True if the patch changed.
     */
    fun advance(patch: FarmingPatch, now: Long): Boolean {
        val last = patch.lastUpdated
        patch.lastUpdated = now
        var changed = patch.advanceTimers(now)
        if (last <= 0L) {
            return changed
        }
        if (patch.hasPlant()) {
            val cycles = boundaries(last, now, patch.cycleMinutes)
            for (i in 0 until cycles) {
                if (!patch.tick()) {
                    break
                }
                changed = true
            }
        } else {
            val cycles = minOf(boundaries(last, now, WEED_CYCLE_MINUTES), FarmingPatch.MAX_WEEDS.toLong())
            for (i in 0 until cycles) {
                if (patch.growWeeds()) {
                    changed = true
                }
            }
        }
        return changed
    }

    /**
     * Returns how many multiples of [minutes] lie after [from] and up to and including [to], both in epoch
     * milliseconds.
     */
    fun boundaries(from: Long, to: Long, minutes: Int): Long {
        if (to <= from) {
            return 0
        }
        val cycleMillis = minutes * 60_000L
        return to / cycleMillis - from / cycleMillis
    }
}
