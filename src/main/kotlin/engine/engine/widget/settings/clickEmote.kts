package engine.widget.settings

import api.predef.*
import api.bot.isRealBot
import api.bot.social.BotIdleEmotes
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.bot.Bot

// Map all button interactions.
for (entry in Emote.BUTTON_TO_EMOTE.entries) {
    button(entry.key) {
        if (plr.isRealBot() && !BotIdleEmotes.accept(plr as Bot, entry.key)) return@button
        plr.animation(Animation(entry.value.id))
    }
}

// Unlock all emotes.
on(LoginEvent::class, EventPriority.NORMAL) {
    plr.sendVarbits(313, Emote.UNLOCK_EMOTE_VARBITS)
    plr.sendVarbit(Emote.UNLOCK_GOBLIN_EMOTE_VARBIT)
}
