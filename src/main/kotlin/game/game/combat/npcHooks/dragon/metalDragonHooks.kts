package game.combat.npcHooks.dragon

import api.combat.npc.NpcCombatHandler.combat

/**
 * Registers native metal dragon breathing and melee for the three ordinary revision 377 species.
 *
 * @author lare96
 */
combat(1590, 1591, 1592) {
    afterRetaliate { enemy, action -> DragonCombat.retaliate(this, enemy, action) }
    attack { DragonCombat.attack(this, metal = true) }
}
