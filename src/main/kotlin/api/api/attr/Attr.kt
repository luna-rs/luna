package api.attr

import api.attr.json.ActiveSlayerTaskTypeAdapter
import api.attr.json.AttributeMapTypeAdapter
import api.attr.json.IndexedItemTypeAdapter
import api.attr.json.ItemContainerTypeAdapter
import api.attr.json.ItemTypeAdapter
import api.predef.*
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import com.google.gson.stream.JsonReader
import game.skill.slayer.ActiveSlayerTask
import io.luna.game.TickTimer
import io.luna.game.action.TimeSource
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.attr.Attributable
import io.luna.game.model.mob.attr.Attribute
import io.luna.game.model.mob.attr.AttributeMap
import kotlin.reflect.KClass

/**
 * A factory class for instantiating attributes.
 *
 * @author lare96
 */
object Attr {

    init {
        val builder = GsonBuilder().disableHtmlEscaping().disableInnerClassSerialization().setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)

        // Register special types.
        builder.registerTypeAdapter(ItemContainer::class.java, ItemContainerTypeAdapter)
        builder.registerTypeAdapter(ActiveSlayerTask::class.java, ActiveSlayerTaskTypeAdapter)
        builder.registerTypeAdapter(AttributeMap::class.java, AttributeMapTypeAdapter)
        builder.registerTypeAdapter(IndexedItem::class.java, IndexedItemTypeAdapter)
        builder.registerTypeAdapter(Item::class.java, ItemTypeAdapter)

        // Set the serializer.
        Attribute.setGsonInstance(builder.create())
    }

    /**
     * Creates a new [Int] attribute with [initialValue] (default `0`).
     */
    fun int(initialValue: () -> Int = { 0 }) = attribute(initialValue)

    /**
     * Creates a [Long] attribute with [initialValue] (default `0L`).
     */
    fun long(initialValue: () -> Long = { 0L }) = attribute(initialValue)

    /**
     * Creates a [String] attribute with [initialValue] (default `""`).
     */
    fun string(initialValue: () -> String = { "" }) = attribute(initialValue)

    /**
     * Creates a [Double] attribute with [initialValue] (default `0.0`).
     */
    fun double(initialValue: () -> Double = { 0.0 }) = attribute(initialValue)

    /**
     * Creates a [Boolean] attribute with [initialValue] (default `false`).
     */
    fun boolean(initialValue: () -> Boolean = { false }) = attribute(initialValue)

    /**
     * Creates a general purpose [Object]/[Any] attribute. If [Attribute.persist] is chained, please consider writing
     * a custom [TypeAdapter] to control serialization.
     */
    fun <E : Any> nullableObj(type: KClass<E>, initialValue: () -> E? = { null }): AttributeDelegate<E?> {
        val attr = Attribute(type.java, initialValue)
        return AttributeDelegate(attr)
    }

    /**
     * Creates a general purpose [Object]/[Any] attribute. If [Attribute.persist] is chained, please consider writing
     * a custom [TypeAdapter] to control serialization.
     */
    inline fun <reified E : Any> obj(noinline initialValue: () -> E): AttributeDelegate<E> =
        attribute(initialValue)

    /**
     * Creates a [TimeSource] attribute.
     */
    fun timeSource(): AttributeDelegate<TimeSource> {
        val attr = Attribute(TimeSource::class.java) { TimeSource(world) }
        return AttributeDelegate(attr)
    }

    /**
     * Creates a [TickTimer] attribute.
     */
    fun timer(): AttributeDelegate<TickTimer> {
        val attr = Attribute(TickTimer::class.java) { TickTimer(world) }
        return AttributeDelegate(attr)
    }

    /**
     * Creates an [ArrayList] attribute with [initialValues].
     */
    fun <E> list(initialValues: () -> ArrayList<E> = { ArrayList() }): AttributeDelegate<ArrayList<E>> =
        attribute(initialValues)

    /**
     * Creates a [HashSet] attribute with [initialValues].
     */
    fun <E> hashSet(initialValues: () -> HashSet<E> = { HashSet() }): AttributeDelegate<HashSet<E>> =
        attribute(initialValues)

    /**
     * Creates a [HashMap] attribute with [initialValues].
     */
    fun <K, V> map(initialValues: () -> HashMap<K, V> = { HashMap() }): AttributeDelegate<HashMap<K, V>> =
        attribute(initialValues)

    /**
     * Validates and reads a json member. Intended to be used within [TypeAdapter]s.
     */
    internal fun <E> readJsonMember(reader: JsonReader, expected: String, valueProducer: (JsonReader) -> E): E {
        val next = reader.nextName()
        return if (next.equals(expected)) valueProducer(reader) else
            throw IllegalArgumentException("Expected [$expected], got [$next]")
    }

    /**
     * Creates a new attribute delegate for values of type [T].
     *
     * The runtime type token for [T] is captured using a reified type parameter and passed to [Attribute]. The
     * [initial] factory is stored and used to lazily create the default value the first time this attribute is
     * accessed for a given [Attributable].
     *
     * @param T The type of value stored in this attribute.
     * @param initial A factory used to create the default value when the attribute is first read.
     * @return An [AttributeDelegate] that reads and writes this attribute through the owner's attribute map.
     */
    inline fun <reified T> attribute(noinline initial: () -> T): AttributeDelegate<T> {
        val attr = Attribute(T::class.java, initial)
        return AttributeDelegate(attr)
    }
}