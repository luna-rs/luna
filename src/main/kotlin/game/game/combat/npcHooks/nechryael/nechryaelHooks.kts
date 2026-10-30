package game.combat.npcHooks.nechryael

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.item.Equipment.EquipmentBonus

/**
 * Replaces Nechryael melee turns with the native chance to summon a temporary death spawn.
 *
 * @author lare96
 */
combat(1613) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        Nechryael.attack(npc, other)
    }
}
