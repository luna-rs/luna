package world.player.emote

import api.predef.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * Performs an emote if the pressed button corresponds to an emote button.
 */
fun performEmote(plr: Player, button: Int) {
    val emote = Emote.BUTTON_TO_EMOTE[button];
    if (emote != null) {
        plr.animation(Animation(emote.id));
    }
}

/**
 * Forward to [performEmote].
 */
on(ButtonClickEvent::class) { performEmote(plr, id) }
