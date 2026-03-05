package engine.combat.prayer

import api.predef.*

// Dynamically add listeners for all prayers.
for (prayer in CombatPrayer.VALUES) {
    button(prayer.button) {
        plr.combat.prayers.activate(prayer)
    }
}