package engine.combat.weapons

import api.predef.*
import io.luna.game.model.def.CombatStyleDefinition

/**
 * All unique interface button ids that map to selectable combat styles.
 *
 * This is built from [CombatStyleDefinition.ALL], excluding styles that do not expose a button id.
 */
val buttons = HashSet<Int>(CombatStyleDefinition.ALL.size)

// Collect every valid combat-style button id.
for (style in CombatStyleDefinition.ALL.values) {
    if (style.button == -1) {
        continue
    }
    buttons.add(style.button)
}

// Registers a click handler for each unique combat-style button
for (id in buttons) {
    button(id) {
        plr.combat.weapon.changeStyle(id)
    }
}