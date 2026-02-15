package game.bot.injector

import api.predef.*
import api.predef.ext.*
import engine.interaction.follow.MobFollowAction
import io.luna.game.event.impl.ChatEvent

// An example of bot context injectors.
injector(ChatEvent::class).
    filter { msg.unpackedMessage.contains(bot.username) }.
    then {
        if(!bot.actions.contains(MobFollowAction::class)) {
            bot.follow(msg.plr)
            bot.output.chat("Hi ${msg.plr.username}. I like it when you say my name ;)")
        }
    }