package game.skill.magic.superheatItem

import api.predef.*
import io.luna.game.event.impl.UseSpellEvent.MagicOnItemEvent

/* Interaction for superheat spell. */
on(MagicOnItemEvent::class).filter { spellId == SuperheatItemAction.SPELL_ID }
    .then { plr.submitAction(SuperheatItemAction(plr, targetItemIndex)) }