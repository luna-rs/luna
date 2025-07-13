package world.player.following

import api.predef.INTERACTION_FOLLOW
import api.predef.on
import io.luna.game.event.impl.PlayerClickEvent.PlayerThirdClickEvent

/**
 * Follows the target when the correct option is clicked.
 */
on(PlayerThirdClickEvent::class)
    .filter { plr.interactions.contains(INTERACTION_FOLLOW) }
    .then { plr.follow(targetPlr) }