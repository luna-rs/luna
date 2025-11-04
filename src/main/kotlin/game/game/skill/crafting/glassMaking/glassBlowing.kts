package game.skill.crafting.glassMaking

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.NumberInput


/**
 * Starts an action if the [GlassBlowingInterface] is open.
 */
fun addAction(plr: Player, material: GlassMaterial, amount: Int) {
    if (GlassBlowingInterface::class in plr.overlays) {
        plr.submitAction(GlassBlowingActionItem(plr, material, amount))
    }
}

// Use glassblowing pipe on molten glass to open interface.
useItem(1785).onItem(1775) { plr.overlays.open(GlassBlowingInterface()) }

// Register all glassblowing interface action listeners.
GlassMaterial.values().forEach {
    button(it.make1Id) { addAction(plr, it, 1) }
    button(it.make5Id) { addAction(plr, it, 5) }
    button(it.make10Id) { addAction(plr, it, 10) }
    button(it.makeXId) {
        plr.overlays.open(object : NumberInput() {
            override fun input(player: Player?, value: Int) {
                addAction(plr, it, value)
            }
        })
    }
}
