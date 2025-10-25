package api.attr

import io.luna.game.model.mob.attr.Attributable
import io.luna.game.model.mob.attr.Attribute
import kotlin.reflect.KProperty

/**
 * An extension property that adds a getter delegate to [Attribute].
 */
operator fun <T> Attribute<T>.getValue(obj: Attributable, property: KProperty<*>): T = obj.attributes()[this]

/**
 * An extension property that adds a setter delegate to [Attribute].
 */
operator fun <T> Attribute<T>.setValue(obj: Attributable, property: KProperty<*>, value: T) {
   obj.attributes()[this] = value
}