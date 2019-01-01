package api.attr

import api.predef.*
import io.luna.game.model.mob.Mob
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
class Stopwatch(name: String) : Attr<Long>(name) {

    /**
     * Retrieves the difference between now and the last call to [setValue].
     */
    override operator fun getValue(mob: Mob, property: KProperty<*>): Long {
        val value = attr(mob).get()
        return when (value) {
            0L -> Long.MAX_VALUE // TODO Workaround for timer initialization.
            else -> currentTimeMs() - value
        }
    }

    /**
     * Resets the value to [currentTimeMs], regardless of the [value] argument. The recommended convention
     * is to simply assign -1.
     */
    override operator fun setValue(mob: Mob, property: KProperty<*>, value: Long) =
        attr(mob).set(currentTimeMs())
}