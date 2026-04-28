package game.obj.switchSpellbook

import api.combat.magic.CombatSpellHandler.resetAutocast
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Spellbook
import game.player.Animations

// Switch spell books when the altar is clicked, reset auto-cast state.
object1(6552) {
    plr.animation(Animations.PRAY)
    if (plr.spellbook == Spellbook.REGULAR) {
        plr.sendMessage("You switch to ancient magicks.")
        plr.spellbook = Spellbook.ANCIENT
    } else {
        plr.sendMessage("You switch back to regular magic.")
        plr.spellbook = Spellbook.REGULAR
    }
    plr.combat.magic.resetAutocast()
}
