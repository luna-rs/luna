package engine.player

import api.bot.zone.SubZone
import api.predef.useItem
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportStyle

val DEV_ITEM = 7500

useItem(DEV_ITEM).onPlayer {
    // todo open dialogue menu
    // todo player info, kick, ban, mute, move options
    if(targetPlr.isBot) {
        println(targetPlr.asBot().scriptStack.current())
        println(targetPlr.asBot().scriptStack.size())
        println(targetPlr.combatLevel)
    }
    targetPlr.teleport(SubZone.HOME.inside, TeleportStyle.REGULAR)
}