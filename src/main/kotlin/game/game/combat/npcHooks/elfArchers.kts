package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.mob.combat.AmmoType

// Level 90 crystal-bow elves; the level 108 halberd variants remain melee.
combat(1183, 2361, 2362) {
    attack { ranged(AmmoType.CRYSTAL_ARROW, animationId = 426) }
}
