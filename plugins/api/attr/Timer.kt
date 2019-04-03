package api.attr

import io.luna.game.action.ThrottledAction.TimeSource
import java.util.concurrent.TimeUnit

/**
 * A model representing a stopwatch, used for measuring elapsed time. Uses [System.nanoTime] for the best accuracy. Implements
 * [Comparable] so comparative operators can be used on Kotlin instances.
 *
 * @author lare96 <http://github.com/lare96>
 */
class Timer(initialDuration: Long) : TimeSource() {

    /**
     * The point at which to begin measuring elapsed time.
     */
    private var snapshot: Long = System.nanoTime() - initialDuration

    override fun getDuration() = getDuration(TimeUnit.MILLISECONDS)

    override fun resetDuration() {
        reset()
    }

    /**
     * Resets this stopwatch's [getDuration] to `0`.
     */
    fun reset(): Timer {
        snapshot = System.nanoTime()
        return this
    }

    /**
     * Returns the duration between [snapshot] and now, in [timeUnit].
     */
    fun getDuration(timeUnit: TimeUnit) = timeUnit.convert(System.nanoTime() - snapshot, TimeUnit.NANOSECONDS)
}