package api

import io.luna.game.model.mob.Player
import kotlin.reflect.KProperty

/**
 * A model representing a delegate for attributes to be retrieved and set through properties. The syntax for
 * usage is as follows
 *
 * ```
 *
 * var Player.myAttribute by Attr<Int>("attribute_name")
 *
 * fun doSomething(plr: Player) {
 *     plr.myAttribute = 10
 *     println(plr.myAttribute)
 * }
 * ```
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

/**
 * A model representing a delegate for attribute timers. It includes functions for measuring elapsed time and
 * being reset. The syntax for usage is as follows
 *
 * ```
 *
 * var Player.myWatch by Stopwatch("stopwatch_name")
 *
 * fun doSomething(plr: Player) {
 *     // Check if elapsed time > 2500ms
 *     if(plr.myWatch > 2500) {
 *         ...
 *
 *         // Reset the elapsed time to 0
 *         plr.myWatch = -1
 *     }
 * }
 * ```
 */
class Stopwatch(private val name: String) {

    /**
     * Retrieves the difference between now and the last call to [setValue].
     */
    operator fun getValue(player: Player, property: KProperty<*>): Long {
        val attrValue = player.attributes.get<Long>(name).get()
        return when (attrValue) {
            0L -> Long.MAX_VALUE // There was never a last call.
            else -> currentTimeMs() - attrValue
        }
    }

    /**
     * Resets the value to [currentTimeMs], regardless of the [value] argument. The recommended convention
     * is to simply assign -1.
     */
    operator fun setValue(player: Player, property: KProperty<*>, value: Long) {
        val attr = player.attributes.get<Long>(name)
        attr.set(currentTimeMs())
    }
}
