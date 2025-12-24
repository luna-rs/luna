package api.predef.ext

import io.luna.game.action.Action
import io.luna.game.action.ActionQueue
import kotlin.reflect.KClass

/**
 * An implementation of [ActionQueue.contains] for [KClass].
 */
operator fun ActionQueue.contains(type: KClass<out Action<*>>) = contains(type.java)