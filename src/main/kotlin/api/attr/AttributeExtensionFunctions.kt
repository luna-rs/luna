package api.attr

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.Attribute
import kotlin.reflect.KProperty

/**
 * An extension property that adds a getter delegate to [Attribute].
 */
operator fun <T> Attribute<T>.getValue(plr: Player, property: KProperty<*>): T = plr.attributes[this]

/**
 * An extension property that adds a setter delegate to [Attribute].
 */
operator fun <T> Attribute<T>.setValue(plr: Player, property: KProperty<*>, value: T) {
    plr.attributes[this] = value
}