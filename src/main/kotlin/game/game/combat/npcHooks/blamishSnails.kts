package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.mob.combat.attack.RangedCombatAttack

// Snails deal ranged damage at melee distance. The original acid projectile is unverified;
// do not substitute the unrelated Temple Trekking giant-snail projectile.
combat(1227, 1228, 1229, 1230, 1231, 1232, 1233, 1234, 1235) {
    attack {
        RangedCombatAttack(npc, other, 1277, null, null, null, npc.combatDef().attackSpeed, 1)
    }
}
