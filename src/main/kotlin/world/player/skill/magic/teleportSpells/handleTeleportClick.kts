package world.player.skill.magic.teleportSpells

import api.predef.*
import io.luna.util.StringUtils

/* Intercept all button clicks.*/
for (spell in TeleportSpell.VALUES) {
    button(spell.button) {
        plr.submitAction(object : TeleportAction(plr, spell.level, spell.destination, spell.style, spell.requirements) {
            override fun onTeleport() {
                plr.sendMessage("You teleport to ${StringUtils.capitalize(spell.displayName)}.")
                plr.magic.addExperience(spell.xp)
            }
        })
    }
}