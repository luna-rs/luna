package world.player.skill.crafting.glassMaking

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface

/**
 * Starts an action if the [GlassBlowingInterface] is open.
 */
fun addAction(plr: Player, material: GlassMaterial, amount: Int) {
    if (plr.interfaces.isOpen(GlassBlowingInterface::class)) {
        plr.submitAction(GlassBlowingActionItem(plr, material, amount))
    }
}

// Use glassblowing pipe on molten glass to open interface.
useItem(1785).onItem(1775) { plr.interfaces.open(GlassBlowingInterface()) }

// Register all glassblowing interface action listeners.
GlassMaterial.values().forEach {
    button(it.make1Id) { addAction(plr, it, 1) }
    button(it.make5Id) { addAction(plr, it, 5) }
    button(it.make10Id) { addAction(plr, it, 10) }
    button(it.makeXId) {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player?, value: Int) {
                addAction(plr, it, value)
            }
        })
    }
}
