package api.attr

import api.attr.typeAdapters.ItemContainerTypeAdapter
import api.predef.*
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import io.luna.game.action.TimeSource
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.attr.Attribute
import io.luna.util.TickTimer
import kotlin.reflect.KClass

/**
 * A factory class for instantiating attributes.
 *
 * @author lare96
 */
object Attr {

    /**
     * A set of valid collection types.
     */
    val VALID_COLLECTION_TYPES = setOf(Int::class, Long::class, String::class, Double::class, Boolean::class)

    init {
        val builder = GsonBuilder().disableHtmlEscaping().disableInnerClassSerialization().setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)

        // Register special types.
        Attribute.addSpecialType(builder, ItemContainer::class.java, ItemContainerTypeAdapter)

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
     * Creates a general purpose [Object]/[Any] attribute. If [Attribute.persist] is chained, please consider writing
     * a custom [TypeAdapter] to control serialization.
     */
    fun <E : Any> nullableObj(type: KClass<E>, initialValue: E? = null): AttributeDelegate<E?> {
        val attr = Attribute(type.java, initialValue)
        return AttributeDelegate(attr)
    }
    /**
     * Creates a general purpose [Object]/[Any] attribute. If [Attribute.persist] is chained, please consider writing
     * a custom [TypeAdapter] to control serialization.
     */
    fun <E : Any> obj(initialValue: E): AttributeDelegate<E> { // NON-NULL
        val attr = Attribute(initialValue)
        return AttributeDelegate(attr)
    }
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

    /**
     * Creates an [ArrayList] attribute with [initialValues].
     */
    inline fun <reified E> list(vararg initialValues: E): AttributeDelegate<ArrayList<E>> {
        check(VALID_COLLECTION_TYPES.contains(E::class)) { "Attribute collections can only hold Int, Long, Double, Boolean, or String types." }
        val values = initialValues.toCollection(ArrayList())
        val attr = Attribute(values)
        return AttributeDelegate(attr)
    }

    /**
     * Creates a [HashMap] attribute with [initialValues].
     */
    inline fun <reified K, reified V> map(vararg initialValues: Pair<K, V>): AttributeDelegate<HashMap<K, V>> {
        check(VALID_COLLECTION_TYPES.contains(K::class) && VALID_COLLECTION_TYPES.contains(V::class)) {
            "Attribute collections can only hold Int, Long, Double, Boolean, or String types."
        }
        val map = HashMap<K, V>()
        for (next in initialValues) {
            map += next
        }
        val attr = Attribute(map)
        return AttributeDelegate(attr)
    }

    /**
     * Validates and reads a json member. Intended to be used within [TypeAdapter]s.
     */
    internal fun <E> readJsonMember(reader: JsonReader, expected: String, valueProducer: (JsonReader) -> E): E {
        val next = reader.nextName()
        return if (next.equals(expected)) valueProducer(reader) else
            throw IllegalArgumentException("Expected [$expected], got [$next]")
    }
}