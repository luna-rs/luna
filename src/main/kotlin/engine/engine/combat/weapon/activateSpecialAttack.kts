package engine.combat.weapon

import api.predef.*

/**
 * All special attack buttons.
 */
val SPECIAL_ATTACK_BUTTONS = setOf(7562, 7587, 7687, 7512, 7537, 7637, 7612, 7487, 8481, 7662, 7462, 7788, 12311)

// Loop through all buttons and make them toggle the special bar when clicked.
for (id in SPECIAL_ATTACK_BUTTONS) {
    button(id) { plr.combat.specialBar.toggle() }
}