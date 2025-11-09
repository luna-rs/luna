package game.bot.injector

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ChatEvent

// An example of bot context injectors.
injector(ChatEvent::class).
    filter { msg.unpackedMessage.contains(bot.username) }.
    then {
        if(rand(10) == 0) {
            bot.follow(msg.plr)
            bot.output.chat("Hi ${msg.plr.username}, you called me??")
        }
    }