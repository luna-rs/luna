package api.attr

import api.predef.*
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import io.luna.game.action.TimeSource
import io.luna.game.model.mob.attr.Attribute
import io.luna.util.TickTimer

/**
 * A factory class for instantiating attributes.
 *
 * @author lare96 <http://github.com/lare96>
 */
object Attr {

    init {
        val builder = GsonBuilder().disableHtmlEscaping().disableInnerClassSerialization().setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)

        // Register type adapters.
        builder.registerTypeAdapter(TickTimer::class.java, TickTimerTypeAdapter)

        // Set the serializer.
        Attribute.setGsonInstance(builder.create())
    }

    /**
     * Creates a new [Int] attribute with [initialValue] (default `0`).
     */
    fun int(initialValue: Int = 0) = AttributeDelegate(Attribute(initialValue))

    /**
     * Creates a [Long] attribute with [initialValue] (default `0L`).
     */
    fun long(initialValue: Long = 0L) = AttributeDelegate(Attribute(initialValue))

    /**
     * Creates a [String] attribute with [initialValue] (default `""`).
     */
    fun string(initialValue: String = "") = AttributeDelegate(Attribute(initialValue))

    /**
     * Creates a [Double] attribute with [initialValue] (default `0.0`).
     */
    fun double(initialValue: Double = 0.0) = AttributeDelegate(Attribute(initialValue))

    /**
     * Creates a [Boolean] attribute with [initialValue] (default `false`).
     */
    fun boolean(initialValue: Boolean = false) = AttributeDelegate(Attribute(initialValue))

    /**
     * Creates a [TimeSource] attribute.
     */
    fun timeSource(): AttributeDelegate<TimeSource> {
        val attr = Attribute(TimeSource(world))
        return AttributeDelegate(attr)
    }

    /**
     * Creates a [TickTimer] attribute with [initialTicks] (default `0L`).
     */
    fun timer(initialTicks: Long = 0L): AttributeDelegate<TickTimer> {
        val attr = Attribute(TickTimer(world, initialTicks))
        return AttributeDelegate(attr)
    }
}