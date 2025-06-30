package api.attr

import io.luna.game.model.Entity
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.Attribute
import kotlin.reflect.KProperty

/**
 * An extension property that adds a getter delegate to [Attribute].
 */
operator fun <T> Attribute<T>.getValue(entity: Entity, property: KProperty<*>): T = entity.attributes[this]

/**
 * An extension property that adds a setter delegate to [Attribute].
 */
operator fun <T> Attribute<T>.setValue(entity: Entity, property: KProperty<*>, value: T) {
    entity.attributes[this] = value
}