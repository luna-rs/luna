package world.player.skill.magic.telekineticGrab

import api.predef.*
import io.luna.game.event.impl.UseSpellEvent.MagicOnGroundItemEvent

on(MagicOnGroundItemEvent::class)
    .filter { spellId == -1 }
    .then { }


// TODO use raycast to determine if you can reach it? or what are the rules? maybe make raycast
//  return a list of blocked objects then determine by type?