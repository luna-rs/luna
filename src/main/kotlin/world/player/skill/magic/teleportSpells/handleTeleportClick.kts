package world.player.skill.magic.teleportSpells

import api.predef.*
import io.luna.util.StringUtils
import world.player.skill.magic.teleportSpells.TeleportAction.Companion.teleportDelay

/* Intercept all button clicks.*/
for (spell in TeleportSpell.VALUES) {
    button(spell.button) {
        if (plr.teleportDelay.ready(2)) { // So player can't button spam.
            plr.submitAction(object : TeleportAction(plr,
                                                     spell.level,
                                                     spell.xp,
                                                     spell.destination,
                                                     spell.style,
                                                     spell.requirements) {
                override fun onTeleport() {
                    plr.sendMessage("You teleport to ${StringUtils.capitalize(spell.displayName)}.")
                }
            })
        }
    }
}