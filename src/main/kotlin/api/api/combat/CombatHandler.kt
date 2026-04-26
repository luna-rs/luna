package api.combat

import api.predef.*
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WeaponAnimationDefinition
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.combat.Weapon
import io.luna.game.model.mob.combat.state.PlayerCombatContext
import io.luna.game.model.mob.combat.state.PlayerCombatWeapon
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.varp.Varp
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * Handles shared combat-interface update behavior.
 *
 * @author lare96
 */
object CombatHandler {

    /**
     * Refreshes the player's combat overlay for their currently equipped weapon.
     *
     * This updates both the displayed weapon interface content and the active combat tab interface id based on the
     * player's current weapon type.
     */
    fun PlayerCombatContext.displayCombatOverlay() {
        weapon.display(mob.equipment.weapon?.id)
        mob.tabs.set(TabIndex.COMBAT, weapon.typeDef.id)
    }

    /**
     * Updates this weapon's combat tab interface for its owning player.
     *
     * This writes the appropriate combat tab widget, displays the equipped item model when present, writes the weapon
     * name text, toggles the special attack bar when applicable, and updates the selected combat-style varp.
     *
     * @param id The equipped weapon item id, or `null` if the player is unarmed.
     */
    fun PlayerCombatWeapon.display(id: Int?) {
        player.tabs[TabIndex.COMBAT] = typeDef.id
        if (id != null) {
            // Send item model onto weapon overlay.
            player.queue(WidgetItemModelMessageWriter(typeDef.id + 1, 200, id))
        }

        // Send text onto weapon overlay.
        if (typeDef.type == Weapon.CROSSBOW || typeDef.type == Weapon.WHIP) {
            player.queue(WidgetTextMessageWriter("Weapon: ", typeDef.line - 1))
        }
        player.queue(WidgetTextMessageWriter(if (id == null) "Unarmed" else
                                                 " " + ItemDefinition.ALL.retrieve(id).name, typeDef.line))
        val special = typeDef.special
        if (special != null) {
            // Only proceed if the interface supports the special bar.
            if (specialAttackType != null) {
                // Only show special bar if the item has a special attack.
                player.queue(WidgetVisibilityMessageWriter(special.bar, false))
                player.combat.specialBar.update()
            } else {
                player.queue(WidgetVisibilityMessageWriter(special.bar, true))
            }
        }
        player.sendVarp(Varp(43, styleDef.config))
    }

    /**
     * Updates this weapon's owning player's model animations to match the weapon's animation definition.
     *
     * If the weapon defines a custom animation model, its standing, walking, and running animations are applied.
     * Otherwise, the default player model animations are restored.
     */
    fun PlayerCombatWeapon.updateModelAnimations() {
        var model = WeaponAnimationDefinition.ALL[id]?.model // Try weapon animation override linked model.
        if (model == null) {
            model = def.typeDef.model // Try weapon interface linked model.
        }
        if (model != null) {
            // We found a match, set the player model animations.
            player.model = PlayerModelAnimation.Builder()
                .setStandingId(model.standing)
                .setWalkingId(model.walking)
                .setRunningId(model.running)
                .build()
        } else {
            // No model data for this weapon/interface, use default values.
            player.model = PlayerModelAnimation.DEFAULT
        }
    }
}