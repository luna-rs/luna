package world.player.emote

import api.predef.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

// Map all button interactions.
for (entry in Emote.BUTTON_TO_EMOTE.entries) {
    button(entry.key) { plr.animation(Animation(entry.value.id)) }
}
