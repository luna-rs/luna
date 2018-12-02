package api

import io.luna.game.model.mob.Player
import kotlin.reflect.KProperty

/**
 * A delegate that will retrieve and set attribute values.
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
 * A delegate for timer attributes.
 */
class TimerAttr(private val name: String) {
    // TODO Better solution for timer attributes?

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
     * Resets the value to [currentTimeMs]. A value other than [RESET_TIMER] will have no effect.
     */
    operator fun setValue(player: Player, property: KProperty<*>, value: Long) {
        if (value == RESET_TIMER) {
            val attr = player.attributes.get<Long>(name)
            attr.set(currentTimeMs())
        }
    }
}

/**
 * A value passed to reset all delegate [Timer] properties.
 */
const val RESET_TIMER: Long = -1
