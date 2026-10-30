package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat

/**
 * Both bloodveld forms use melee offence against magic defence, retaining Protect from Melee and ordinary
 * contact attack timing. Their native profiles supply animation, attack speed, and maximum damage.
 *
 * @author lare96
 */
combat(1618, 1619) {
    attack {
        melee(magicDefence = true)
    }
}
