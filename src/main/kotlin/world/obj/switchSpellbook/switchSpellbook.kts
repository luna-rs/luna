import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Spellbook
import world.player.Animations

object1(6552) {
    plr.animation(Animations.PRAY)
    if (plr.spellbook == Spellbook.REGULAR) {
        plr.sendMessage("You switch to ancient magicks.")
        plr.spellbook = Spellbook.ANCIENT
    } else {
        plr.sendMessage("You switch back to regular magic.")
        plr.spellbook = Spellbook.REGULAR
    }
}
