package engine.combat.autocast

import api.combat.CombatHandler.displayCombatOverlay
import api.combat.magic.CombatSpellHandler.resetAutocast
import api.predef.*
import game.skill.magic.Magic
import game.skill.magic.Staff
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

/**
 * Checks whether the player is currently in an invalid state for autocast-related actions.
 *
 * A player is considered invalid when:
 * - No weapon is equipped.
 * - The equipped weapon is not treated as a staff.
 * - The player is on the Ancient spellbook but the equipped staff does not support Ancient autocasting.
 *
 * @param plr The player being validated.
 * @return `true` if the player cannot perform the autocast action, or `false` otherwise.
 */
fun invalid(plr: Player): Boolean {
    val weaponId = plr.equipment.weapon?.id
    if (weaponId == null || plr.combat.weapon.type != Weapon.STAFF) {
        // TODO Flag suspicious? Changing autocast button with no/invalid weapon equipped.
        return true
    } else if (plr.spellbook == Spellbook.ANCIENT && weaponId !in Staff.AUTOCAST_ANCIENTS) {
        plr.sendMessage("You cannot auto-cast Ancient Magicks with this staff.")
        return true
    }
    return false
}

// Loop through all spells and dynamically add listeners for their buttons.
for (spell in CombatSpellDefinition.ALL) {
    if (spell.button != -1) {
        button(spell.button) {
            val requirements = Magic.checkRequirements(plr, spell.level, spell.required, true)
            if (requirements != null) {
                plr.combat.magic.autocastSpell = spell
                plr.combat.displayCombatOverlay()
            }
        }
    }
}


// 'Choose spell' button on the combat style overlay.
button(353) {
    if (!invalid(plr)) {
        plr.tabs.set(TabIndex.COMBAT, plr.spellbook.autocastWidget)
    }
}

// Auto-cast button on the combat style overlay.
button(349) {
    if (plr.combat.weapon.type == Weapon.STAFF) {
        val magic = plr.combat.magic
        magic.isAutocasting = !magic.isAutocasting
        magic.refreshAutocast()
    } else {
        // todo flag suspicious?
    }
}

// 'Cancel' button on the auto-cast spell selection overlay.
button(2004) { plr.combat.magic.resetAutocast() } // Regular spellbook.
button(6161) { plr.combat.magic.resetAutocast() } // Ancients' spellbook.


