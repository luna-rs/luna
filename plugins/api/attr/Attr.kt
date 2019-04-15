package api.attr

import api.predef.*
import io.luna.game.action.TimeSource
import io.luna.game.model.mob.attr.Attribute
import io.luna.util.TickTimer

/**
 * A factory class for instantiating attributes.
 *
 * @author lare96 <http://github.com/lare96>
 */
object Attr {

    /**
     * Creates a new [Int] attribute with [initialValue] (default `0`).
     */
    fun int(initialValue: Int = 0) = delegate(Attribute<Int>(initialValue))

    /**
     * Creates a [Long] attribute with [initialValue] (default `0L`).
     */
    fun long(initialValue: Long = 0L) = delegate(Attribute<Long>(initialValue))

    /**
     * Creates a [String] attribute with [initialValue] (default `""`).
     */
    fun string(initialValue: String = "") = delegate(Attribute<String>(initialValue))

    /**
     * Creates a [Double] attribute with [initialValue] (default `0.0`).
     */
    fun double(initialValue: Double = 0.0) = delegate(Attribute<Double>(initialValue))

    /**
     * Creates a [Boolean] attribute with [initialValue] (default `false`).
     */
    fun boolean(initialValue: Boolean = false) = delegate(Attribute<Boolean>(initialValue))

    /**
     * Creates a [TimeSource] attribute.
     */
    fun timeSource(): AttributeDelegate<TimeSource> {
        val attr = Attribute<TimeSource>(TimeSource(world))
        return delegate(attr)
    }

    /**
     * Creates a [TickTimer] attribute with [initialDuration] (default `0L`).
     */
    fun timer(initialDuration: Long = 0L): AttributeDelegate<TickTimer> {
        val attr = Attribute<TickTimer>(TickTimer(world, initialDuration))
        attr.useSerializer(TickTimerSerializer)
        return delegate(attr)
    }

    /**
     * Wraps the attribute in a delegate so they can be used like Kotlin properties.
     */
    private fun <T : Any> delegate(attribute: Attribute<T>) = AttributeDelegate(attribute)
}