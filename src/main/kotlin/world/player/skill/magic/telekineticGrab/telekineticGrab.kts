package world.player.skill.magic.telekineticGrab

import api.predef.*
import io.luna.game.event.impl.UseSpellEvent.MagicOnGroundItemEvent

// Intercept event for telegrab spell.
on(MagicOnGroundItemEvent::class)
    .filter { spellId == 1168 }
    .then { plr.submitAction(TelekineticGrabAction(plr, targetItem)) }