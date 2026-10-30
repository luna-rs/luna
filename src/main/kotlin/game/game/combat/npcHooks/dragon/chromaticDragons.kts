package game.combat.npcHooks.dragon

import api.combat.npc.NpcCombatHandler.combat

/**
 * Adult chromatic dragons share the source's approach/operation cycle and ordinary fire protection. Unlike metal
 * dragons, approach mode may return to melee pursuit from a distance when a direct walking line is available.
 *
 * @author lare96
 */
combat(941, 55, 53, 54) {
    afterRetaliate { enemy, action -> DragonCombat.retaliate(this, enemy, action) }
    attack { DragonCombat.attack(this, metal = false) }
}
