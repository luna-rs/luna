package game.combat.npcHooks.dragon

import api.combat.npc.NpcCombatHandler.combat
import game.content.dragonSlayer.DragonSlayer

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

// Elvarg hunts in melee mode and uses npc_default_retaliate_ap when attacked.
combat(DragonSlayer.ELVARG) {
    afterRetaliate { enemy, action -> DragonCombat.retaliate(this, enemy, action, DragonCombat.Mode.FIRE) }
    attack { DragonCombat.attack(this, metal = false, elvarg = true) }
}
