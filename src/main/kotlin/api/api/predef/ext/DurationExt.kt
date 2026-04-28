package api.predef.ext

import com.google.common.primitives.Ints
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Converts a [java.time.Duration] instance to ticks.
 */
fun java.time.Duration.toTicks(): Int {
    return Ints.saturatedCast(floor(toMillis() / 600.0).toLong())
}

/**
 * Converts a [kotlin.time.Duration] instance to ticks.
 */
fun kotlin.time.Duration.toTicks(): Int {
    return Ints.saturatedCast(floor(inWholeMilliseconds / 600.0).toLong())
}

/**
 * Allows us to represent ticks as a duration from any number.
 */
inline val Number.ticks: Duration get() = (toLong() * 600).milliseconds

/**
 * Converts a [Duration] instance to ticks.
 */
fun kotlin.time.Duration.inTicks(): Int {
    return Ints.saturatedCast(inWholeMilliseconds / 600)
}

