package api.attr

import io.luna.game.model.mob.attr.Attribute
import java.util.concurrent.TimeUnit

/**
 * A factory class for instantiating attributes.
 *
 * @author lare96 <http://github.com/lare96>
 */
object Attr {

    /**
     * Creates a new [Int] attribute with [initialValue] (default `0`).
     */
    fun int(initialValue: Int = 0) = Attribute<Int>(initialValue)

    /**
     * Creates a [Long] attribute with [initialValue] (default `0L`).
     */
    fun long(initialValue: Long = 0L) = Attribute<Long>(initialValue)

    /**
     * Creates a [String] attribute with [initialValue] (default `""`).
     */
    fun string(initialValue: String = "") = Attribute<String>(initialValue)

    /**
     * Creates a [Double] attribute with [initialValue] (default `0.0`).
     */
    fun double(initialValue: Double = 0.0) = Attribute<Double>(initialValue)

    /**
     * Creates a [Boolean] attribute with [initialValue] (default `false`).
     */
    fun boolean(initialValue: Boolean = false) = Attribute<Boolean>(initialValue)

    /**
     * Creates a [Stopwatch] attribute with [initialDuration] in [timeUnit] (default `0ns`).
     */
    fun stopwatch(initialDuration: Long = 0L, timeUnit: TimeUnit = TimeUnit.NANOSECONDS): Attribute<Stopwatch> {
        val newInitialDuration = TimeUnit.NANOSECONDS.convert(initialDuration, timeUnit)
        return Attribute(Stopwatch(newInitialDuration))
    }
}