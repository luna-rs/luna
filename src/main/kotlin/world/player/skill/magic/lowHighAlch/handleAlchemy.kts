package world.player.skill.magic.lowHighAlch

import api.predef.*
import io.luna.game.event.impl.UseSpellEvent.MagicOnItemEvent


/* Intercept events for low and high alchemy. */
on(MagicOnItemEvent::class).filter { spellId == 1162 }
    .then { plr.submitAction(AlchemyAction(plr, AlchemyType.LOW, targetItemIndex)) }

on(MagicOnItemEvent::class).filter { spellId == 1178 }
    .then { plr.submitAction(AlchemyAction(plr, AlchemyType.HIGH, targetItemIndex)) }