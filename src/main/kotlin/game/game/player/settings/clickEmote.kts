package game.player.settings

import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.block.Animation

// Map all button interactions.
for (entry in Emote.BUTTON_TO_EMOTE.entries) {
    button(entry.key) { plr.animation(Animation(entry.value.id)) }
}

// Unlock all emotes.
on(LoginEvent::class) {
    plr.sendVarbits(313, Emote.UNLOCK_EMOTE_VARBITS)
    plr.sendVarbit(Emote.UNLOCK_GOBLIN_EMOTE_VARBIT)
}
