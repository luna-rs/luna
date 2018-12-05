package api.attr

import io.luna.game.model.mob.Player
import kotlin.reflect.KProperty

/**
 * A model representing a delegate for the attribute with [name] and a type of [T]. It can be retrieved and
 * set through the set/get functions in this class. The syntax for usage is as follows
 * ```
 *
 * var Player.myAttribute by Attr<Int>("attribute_name")
 *
 * fun doSomething(plr: Player) {
 *     plr.myAttribute = 10
 *     println(plr.myAttribute)
 * }
 * ```
 *
 * @author lare96
 */
class Attr<T>(private val name: String) {

    /**
     * Retrieve attribute value.
     */
    operator fun getValue(player: Player, property: KProperty<*>): T =
        player.attributes.get<T>(name).get()

    /**
     * Set attribute value.
     */
    operator fun setValue(player: Player, property: KProperty<*>, value: T) =
        player.attributes.get<T>(name).set(value)
}