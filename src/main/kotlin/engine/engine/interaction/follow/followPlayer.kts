package engine.interaction.follow

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.PlayerClickEvent.PlayerThirdClickEvent

/**
 * Follow the player if the correct interaction is in the context menu.
 */
on(PlayerThirdClickEvent::class)
    .filter { plr.contextMenu.contains(OPTION_FOLLOW) }
    .then { plr.follow(targetPlr) }