package api.attr

import api.predef.*
import io.luna.game.model.mob.Player
import kotlin.reflect.KProperty

/**
 * A model representing a delegate for an attribute timer with [name]. It includes functions for measuring
 * elapsed time and being reset. The syntax for usage is as follows
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
 *
 * @author lare96
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