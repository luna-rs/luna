package game.obj.switchSpellbook

import api.combat.magic.CombatSpellHandler.resetAutocast
import api.bot.action.BotCombatActionHandler
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot

// Switch spell books when the altar is clicked, reset auto-cast state.
object1(6552) {
    val bot = plr as? Bot
    if (bot != null && bot.spellbook == Spellbook.REGULAR && !BotCombatActionHandler.canUseAncients(bot)) {
        return@object1
    }
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
