package api.attr

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.Attribute
import kotlin.reflect.KProperty

/**
 * A delegate that forwards to the player's attribute map.
 *
 * @author lare96
 */
class AttributeDelegate<T : Any?>(val attr: Attribute<T>) {

    /**
     * Retrieve the attribute value.
     */
    operator fun getValue(player: Player, property: KProperty<*>): T =
        player.attributes[attr]

    /**
     * Set the attribute value.
     */
    operator fun setValue(player: Player, property: KProperty<*>, value: T) {
        player.attributes[attr] = value
    }

    /**
     * Makes the attribute save permanently.
     */
    fun persist(key: String): Attribute<T> {
        return attr.persist(key)!!
    }
}