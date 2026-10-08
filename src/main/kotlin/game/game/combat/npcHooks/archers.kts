package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.mob.combat.AmmoType

// Ardougne and Burthorpe archers, and the Ranging Guild guard.
combat(27, 678, 1073, 1074, 1075) {
    attack { ranged(AmmoType.IRON_ARROW) }
}

// Guard with a crossbow.
combat(10) {
    attack { ranged(AmmoType.BOLTS, range = 5) }
}

// Ranging Guild tower archers. Each level fires a better arrow.
combat(688) {
    attack { ranged(AmmoType.IRON_ARROW) }
}
combat(689) {
    attack { ranged(AmmoType.STEEL_ARROW) }
}
combat(690) {
    attack { ranged(AmmoType.MITHRIL_ARROW) }
}
combat(691) {
    attack { ranged(AmmoType.ADAMANT_ARROW) }
}
