package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.mob.combat.CombatSpell

// Level 66 infernal mages.
combat(1643, 1644, 1645, 1646, 1647) {
    attack { magic(CombatSpell.FIRE_BLAST) }
}