package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.item.Equipment.EquipmentBonus

/**
 * Corresponding revision 377 ice troll variants share their native crushing melee attack.
 * The bonus is selected for each turn because ordinary combat cleanup clears temporary bonus overrides.
 *
 * @author lare96
 */
val ICE_TROLL_IDS = intArrayOf(1560, 1561, 1562, 1563, 1564, 1565, 1566,
    1936, 1937, 1938, 1939, 1940, 1941, 1942)

combat(*ICE_TROLL_IDS) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee()
    }
}
