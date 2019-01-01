package api.attr

import io.luna.game.model.mob.Mob
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
open class Attr<T>(private val name: String) {

    /**
     * Retrieve attribute value.
     */
    open operator fun getValue(mob: Mob, property: KProperty<*>): T = attr(mob).get()

    /**
     * Set attribute value.
     */
    open operator fun setValue(mob: Mob, property: KProperty<*>, value: T) = attr(mob).set(value)

    /**
     * Retrieves the attribute value instance.
     */
    protected fun attr(mob: Mob) = mob.attributes.get<T>(name)!!
}