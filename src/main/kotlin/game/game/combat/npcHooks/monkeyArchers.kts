package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.mob.combat.AmmoType

// Ape Atoll's ordinary, ravine and posted bow-wielding monkeys.
combat(1456, 1457, 1458) {
    attack { ranged(AmmoType.IRON_ARROW, animationId = 1394) }
}
