package api.predef.ext

import com.google.common.primitives.Ints
import java.time.Duration
import kotlin.time.ExperimentalTime

/**
 * Converts a [Duration] instance to ticks.
 */
fun Duration.toTicks(): Int {
    return Ints.saturatedCast(toMillis() / 600)
}

/**
 * Converts a [Duration] instance to ticks.
 */
@OptIn(ExperimentalTime::class)
fun kotlin.time.Duration.inTicks(): Int {
    return Ints.saturatedCast(toLongMilliseconds() / 600)
}

