package api.attr

import io.luna.game.model.Entity
import io.luna.game.model.item.Item
import io.luna.game.model.mob.attr.Attributable
import io.luna.game.model.mob.attr.Attribute
import kotlin.reflect.KProperty

/**
 * A delegate that forwards to the attribute map.
 *
 * @author lare96
 */
class AttributeDelegate<T : Any?>(val attr: Attribute<T>) {

    /**
     * Retrieve the attribute value.
     */
    operator fun getValue(obj: Attributable, property: KProperty<*>): T = obj.attributes()[attr]

    /**
     * Set the attribute value.
     */
    operator fun setValue(obj: Attributable, property: KProperty<*>, value: T) {
        obj.attributes()[attr] = value
    }

    /**
     * Makes the attribute save permanently.
     */
    fun persist(key: String): Attribute<T> {
        return attr.persist(key)!!
    }
}