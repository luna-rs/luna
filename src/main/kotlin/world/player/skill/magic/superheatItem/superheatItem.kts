package world.player.skill.magic.superheatItem

import api.predef.on
import io.luna.game.event.impl.UseSpellEvent.MagicOnItemEvent

/* Interaction for superheat spell. */
on(MagicOnItemEvent::class).filter { spellId == 1173 }
    .then { plr.submitAction(SuperheatItemAction(plr, targetItemIndex)) }