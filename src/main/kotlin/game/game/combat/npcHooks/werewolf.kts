package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat

/**
 * Registers the citizens' native pre-damage transformation. Wolf forms use their ordinary melee profiles.
 *
 * @author lare96
 */
combat(*Werewolves.FORMS.keys.toIntArray()) {
    defend {
        if (Werewolves.beforeHit(npc, other, damage)) animationId = null
    }
}
