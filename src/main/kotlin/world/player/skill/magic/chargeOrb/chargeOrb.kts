package world.player.skill.magic.chargeOrb

import api.predef.*
import io.luna.game.event.impl.UseSpellEvent.MagicOnObjectEvent

/* Interactions for all charge orb spells. */
for (type in ChargeOrbType.VALUES) {
    on(MagicOnObjectEvent::class).filter { spellId == type.spellId && targetObject.id == type.objectId }
        .then { plr.submitAction(ChargeOrbAction(plr, type)) }
}
