package world.player.following

import api.predef.*
import io.luna.game.event.impl.PlayerClickEvent.PlayerThirdClickEvent

on(PlayerThirdClickEvent::class)
    .filter { plr.interactions.contains(INTERACTION_FOLLOW) }
    .then { plr.submitAction(FollowingAction(plr, targetPlr)) }