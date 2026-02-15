package api.bot.dsl

import io.luna.game.model.mob.bot.Bot
import io.luna.game.event.impl.InjectableEvent

/**
 * A lightweight wrapper class used to bind a specific [Bot] instance to an [InjectableEvent] currently being
 * processed.
 *
 * @param E The concrete type of [InjectableEvent] being handled.
 * @property bot The [Bot] that is currently processing the event.
 * @property msg The [InjectableEvent] message that triggered this context.
 *
 * @author lare96
 */
class BotInjectorWrapper<E : InjectableEvent>(val bot: Bot, val msg: E)