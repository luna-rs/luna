package api.attr

import java.util.concurrent.TimeUnit

/**
 * A model representing a stopwatch, used for measuring elapsed time. Uses {@link System#nanoTime()} for the best
 * accuracy. Implements {@link Comparable} so comparative operators can be used on Kotlin instances.
 *
 * @author lare96 <http://github.com/lare96>
 */
class Stopwatch(initialDuration: Long) : Comparable<Long> {

    /**
     * The point at which to begin measuring elapsed time.
     */
    private var snapshot: Long = System.nanoTime() - initialDuration

    override fun compareTo(other: Long): Int = java.lang.Long.compare(getDuration(), other)

    /**
     * Resets this stopwatch's [getDuration] to `0`.
     */
    fun reset(): Stopwatch {
        snapshot = System.nanoTime()
        return this
    }

    /**
     * Returns the duration between [snapshot] and now, in [timeUnit].
     */
    fun getDuration(timeUnit: TimeUnit = TimeUnit.MILLISECONDS) = timeUnit.convert(System.nanoTime() - snapshot, TimeUnit.NANOSECONDS)

    /**
     * Determines if [duration] in [timeUnit] has elapsed.
     */
    fun hasDurationElapsed(duration: Long, timeUnit: TimeUnit = TimeUnit.MILLISECONDS) = getDuration(timeUnit) >= duration
}