package engine.interaction.follow

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.PlayerClickEvent.PlayerThirdClickEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

val DEFAULT_INTERACTION_POLICY = InteractionPolicy(InteractionType.LINE_OF_SIGHT, Position.VIEWING_DISTANCE)

/**
 * Follow the player if the correct interaction is in the context menu.
 */
on(PlayerThirdClickEvent::class) { _, _ -> DEFAULT_INTERACTION_POLICY }
    .filter { plr.contextMenu.contains(OPTION_FOLLOW) }
    .then { plr.follow(targetPlr) }