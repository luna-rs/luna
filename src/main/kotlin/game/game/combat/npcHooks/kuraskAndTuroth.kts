package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.Luna
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.AmmoType
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.attack.PlayerMeleeCombatAttack
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack

/**
 * Whether the native Slayer-item restriction is enabled; the creature's outgoing attack style is always retained.
 */
val EQUIPMENT_NEEDED = Luna.settings().skills().slayerEquipmentNeeded()

/**
 * Turoth NPC ids.
 */
val TUROTH = setOf(1626, 1627, 1628, 1629, 1630, 1631, 1632)

/**
 * Kurask NPC ids.
 */
val KURASK = setOf(1608, 1609)

/**
 * The leaf-bladed spear item id.
 */
val LEAF_BLADED_SPEAR = 4158

/**
 * Determines whether an attack can damage a Turoth or Kurask.
 *
 * Melee attacks need a leaf-bladed spear, ranged attacks need broad arrows, and magic attacks need the Magic Dart
 * spell. Weapon and ammunition eligibility comes from the committed attack, not current equipment:
 * switching during the delivery delay cannot enable an invalid hit or cancel a valid one. The final broad arrow
 * remains eligible after consumption empties the quiver. Normal launch validation already checks bow compatibility.
 *
 * @param source The attack being defended against.
 * @return `true` if the attack can deal damage, otherwise `false`.
 */
fun canDamage(source: CombatAttack<*>): Boolean =
    when (source) {
        is PlayerMeleeCombatAttack -> source.launchedWeaponId == LEAF_BLADED_SPEAR
        is PlayerRangedCombatAttack -> source.launchedAmmo?.type == AmmoType.BROAD_ARROW
        is MagicCombatAttack<*> -> source.spellEffect?.spell == CombatSpell.MAGIC_DART
        else -> false
    }

for (id in TUROTH + KURASK) {
    /*
     * When slayer equipment restrictions are enabled, players must use a leaf-bladed spear, broad arrows, or Magic
     * Dart to deal damage. Any other attack is negated.
     */
    combat(id) {
        attack {
            if (id in KURASK) combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
            else if (id in 1627..1630) combat.setAttackBonusType(EquipmentBonus.STAB_ATTACK)
            melee()
        }
        defend {
            if (EQUIPMENT_NEEDED && other is Player && !canDamage(source)) {
                damage = null
                other.sendMessage("Your attack seems to have no effect...")
            }
        }
    }
}
