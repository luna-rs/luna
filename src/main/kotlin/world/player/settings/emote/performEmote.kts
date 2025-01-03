package world.player.settings.emote

import api.predef.*
import io.luna.game.model.mob.block.Animation
import world.player.settings.emote.Emote

// Map all button interactions.
for (entry in Emote.BUTTON_TO_EMOTE.entries) {
    button(entry.key) { plr.animation(Animation(entry.value.id)) }
}
