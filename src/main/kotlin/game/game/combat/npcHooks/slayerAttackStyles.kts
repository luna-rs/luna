package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.item.Equipment.EquipmentBonus

/**
 * Lost City attack styles for ordinary Slayer creatures without a separate native attack hook. The bonus is selected
 * for each turn because combat cleanup clears temporary bonus overrides. Existing ranged troll and specialist hooks
 * retain their own registrations. Cave crawler poison severity 36 remains owned by the shared delivered-hit pipeline.
 *
 * @author lare96
 */
val STYLES = mapOf(
    EquipmentBonus.STAB_ATTACK to intArrayOf(49, 1600, 1601, 1602, 1603),
    EquipmentBonus.CRUSH_ATTACK to intArrayOf(132, 1096, 1097, 1098, 1106, 1107, 1108, 1109, 1110, 1111, 1112,
        1118, 1119, 1120, 1121, 1122, 1123, 1124, 1125, 1128, 1129, 1138)
)

for ((bonus, ids) in STYLES) {
    combat(*ids) {
        attack {
            combat.setAttackBonusType(bonus)
            melee()
        }
    }
}
