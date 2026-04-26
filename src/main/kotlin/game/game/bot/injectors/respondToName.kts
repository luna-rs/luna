package game.bot.injectors

import api.predef.*
import io.luna.game.event.impl.ChatEvent

// An example of bot context injectors.
injector(ChatEvent::class).
    filter { msg.unpackedMessage.contains(bot.username) }.
    then {
        if(!bot.navigator.isActive) {
            bot.navigator.follow(msg.plr)
            bot.output.chat("Hi ${msg.plr.username}. I like it when you say my name ;)")
        }
    }