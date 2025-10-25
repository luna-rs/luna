package api.predef.ext

import com.google.common.primitives.Ints
import java.time.Duration

/**
 * Converts a [Duration] instance to ticks.
 */
fun Duration.toTicks(): Int {
    return Ints.saturatedCast(toMillis() / 600)
}

/**
 * Converts a [Duration] instance to ticks.
 */
fun kotlin.time.Duration.inTicks(): Int {
    return Ints.saturatedCast(inWholeMilliseconds / 600)
}

