package engine.combat.weapons

import api.combat.weapons.SpecialAttackHandler
import api.predef.*
import engine.controllers.Controllers.inWilderness
import game.skill.magic.Staff
import io.luna.game.event.impl.EquipmentChangeEvent
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.varp.Varp
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * Updates the combat tab for [plr] using the supplied weapon type definition.
 *
 * @param plr The player whose combat tab will be updated.
 * @param id The equipped weapon item id, or {@code null} if the player is unarmed.
 */
fun display(plr: Player, id: Int?) {
    val weapon = plr.combat.weapon
    val typeDef = weapon.typeDef

    plr.tabs[TabIndex.COMBAT] = typeDef.id
    if (id != null) {
        // Send item model onto weapon overlay.
        plr.queue(WidgetItemModelMessageWriter(typeDef.id + 1, 200, id))
    }

    // Send text onto weapon overlay.
    if (typeDef.type == Weapon.CROSSBOW || typeDef.type == Weapon.WHIP) {
        plr.queue(WidgetTextMessageWriter("Weapon: ", typeDef.line - 1))
    }
    plr.queue(WidgetTextMessageWriter(if (id == null) "Unarmed" else
                                          " " + ItemDefinition.ALL.retrieve(id).name, typeDef.line))
    val special = typeDef.special
    if (special != null) {
        if (SpecialAttackHandler.contains(id)) {
            // Only show special bar if the item has a special attack.
            plr.queue(WidgetVisibilityMessageWriter(special.bar, false))
            plr.combat.specialBar.update()
        } else {
            plr.queue(WidgetVisibilityMessageWriter(special.bar, true))
        }
    }

    val model = weapon.def.model
    if (model != null) {
        // Set the player model animations to the weapon animations.
        plr.model = PlayerModelAnimation.Builder().setStandingId(model.standing).setWalkingId(model.walking)
            .setRunningId(model.running).build()
    } else {
        plr.model = PlayerModelAnimation.DEFAULT
    }
    plr.sendVarp(Varp(43, plr.combat.weapon.styleDef.config))
}

on(EquipmentChangeEvent::class) {
    if (index == Equipment.WEAPON) {
        val weapon = plr.combat.weapon
        weapon.refreshWeapon(weapon.styleDef.stance)
        display(plr, newItem?.id)

        if (plr.inWilderness() || (newItem != null && weapon.type == Weapon.STAFF && // If we're equipping a staff.
                    plr.combat.autocastSpell != CombatSpellDefinition.NONE && // And autocasting.
                    plr.spellbook == Spellbook.ANCIENT && // And using the ancient spellbook.
                    newItem.id !in Staff.AUTOCAST_ANCIENTS)) { // And we cannot cast ancient spells with this staff.
            // Or we're in the wilderness, clear autocasted spell.
            plr.combat.autocastSpell = CombatSpellDefinition.NONE
        }
    }
    plr.combat.refreshAmmo(index)
}

on(LoginEvent::class) {
    display(plr, plr.equipment.weapon?.id)
    plr.combat.refreshAmmo(Equipment.WEAPON)
    plr.sendVarp(Varp(301, 0)) // Deselect special attack bar.
    plr.combat.refreshAutocast()
}