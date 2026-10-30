package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat

/**
 * Bloodvelds, pyrefiends and jellies use melee offence against magic defence, as specified by their Lost City
 * damage styles. These contact attacks retain Protect from Melee, melee timing and native profile damage.
 *
 * @author lare96
 */
combat(1618, 1619, 1633, 1634, 1635, 1636, 1637, 1638, 1639, 1640, 1641, 1642) {
    attack {
        melee(magicDefence = true)
    }
}
