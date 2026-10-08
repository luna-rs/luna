package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.AmmoType

/**
 * The distance gnomes and gnome troops shoot from.
 */
val GNOME_RANGE = 6

/**
 * The iron arrow launch graphic, at a gnome's height.
 */
val GNOME_ARROW_LAUNCH = Graphic(18, 48, 0)

/**
 * The iron arrow projectile.
 */
val GNOME_ARROW = AmmoType.IRON_ARROW.def.projectile

// Gnomes and gnome troops, in the Tree Gnome Stronghold and on the Tree Gnome Village battlefield.
combat(66, 67, 68, 479, 480, 2247, 2248, 2249, 2250, 2251) {
    attack { ranged(GNOME_ARROW_LAUNCH, GNOME_ARROW::apply, range = GNOME_RANGE) }
}

// Gnome women shoot from close up.
combat(168, 169) {
    attack { ranged(GNOME_ARROW_LAUNCH, GNOME_ARROW::apply, range = 1) }
}
