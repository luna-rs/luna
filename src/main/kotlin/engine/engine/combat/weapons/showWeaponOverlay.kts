package engine.combat.weapons

import api.combat.CombatHandler.display
import api.combat.CombatHandler.updateModelAnimations
import api.predef.*
import engine.controllers.Controllers.inWilderness
import game.skill.magic.Staff
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.EquipmentChangeEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.varp.Varp

// Engine level event for weapon equipment changes. Refreshes combat and auto-cast related systems.
on(EquipmentChangeEvent::class, EventPriority.HIGH) {
    if (index == Equipment.WEAPON) {
        val weapon = plr.combat.weapon

        // Resolve new weapon, update combat style interface, update model animations.
        weapon.refreshWeapon(weapon.styleDef.stance)
        weapon.display(newItem?.id)
        weapon.updateModelAnimations()

        if (plr.inWilderness() || (newItem != null && weapon.type == Weapon.STAFF && // If we're equipping a staff.
                    plr.combat.magic.autocastSpell != CombatSpellDefinition.NONE && // And have an auto-cast spell selected.
                    plr.spellbook == Spellbook.ANCIENT && // And using the ancient spell book.
                    newItem.id !in Staff.AUTOCAST_ANCIENTS)) { // And we cannot cast ancient spells with this staff.
            // Or we're in the wilderness, clear auto-casted spell.
            plr.combat.magic.autocastSpell = CombatSpellDefinition.NONE
        } else if (weapon.type == Weapon.STAFF) {
            plr.combat.magic.refreshAutocast()
        }
        plr.combat.specialBar.toggleOff()
    }

    // Resolve new ammo, if applicable.
    if (index == Equipment.WEAPON || index == Equipment.AMMUNITION) {
        plr.combat.ranged.refreshAmmo(index)
    }
}

// Refresh auto-cast, special attack bar selection, and combat style interface on login.
on(LoginEvent::class) {
    plr.combat.weapon.display(plr.equipment.weapon?.id)
    plr.combat.weapon.updateModelAnimations()
    plr.combat.ranged.refreshAmmo(Equipment.WEAPON)
    plr.sendVarp(Varp(301, 0)) // Deselect special attack bar.
    plr.combat.magic.refreshAutocast()
}