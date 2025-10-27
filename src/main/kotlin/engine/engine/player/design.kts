package engine.player

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.DesignPlayerEvent
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag

/**
 * Sets received player appearance values.
 */
on(DesignPlayerEvent::class, EventPriority.HIGH) {
    plr.appearance.setValues(values)
    plr.flags.flag(UpdateFlag.APPEARANCE)
    plr.interfaces.close()
}