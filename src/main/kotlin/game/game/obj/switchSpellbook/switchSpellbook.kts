package game.obj.switchSpellbook

import api.bot.isRealBot

import api.combat.magic.CombatSpellHandler.resetAutocast
import api.bot.action.BotCombatActionHandler
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import game.obj.entrances.Entrance

// The native exit is hidden by a Desert Treasure varbit. Make the portal available with the open altar route.
on(ServerLaunchEvent::class) {
    val exit = Entrance.JALDRAOCHT_PYRAMID_PORTAL
    val position = checkNotNull(exit.position)
    val hidden = world.objects.findAll(position).filter { it.id == 6550 }.findFirst().orElse(null) ?: return@on
    if (world.removeObject(hidden)) {
        world.addObject(exit.id, position, hidden.objectType, hidden.direction)
    }
}

// Switch spell books when the altar is clicked, reset auto-cast state.
object1(6552) {
    val bot = plr as? Bot
    if (bot.isRealBot() && bot.spellbook == Spellbook.REGULAR && !BotCombatActionHandler.canUseAncients(bot)) {
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
    plr.combat.magic.selectedSpell = CombatSpellDefinition.NONE
    plr.combat.magic.resetAutocast()
}
