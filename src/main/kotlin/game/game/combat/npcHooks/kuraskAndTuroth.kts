package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.AmmoType
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.attack.PlayerMeleeCombatAttack
import io.luna.game.model.mob.combat.attack.PlayerRangedCombatAttack

if (Luna.settings().skills().slayerEquipmentNeeded()) {

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
            defend {
                if (other is Player && !canDamage(source)) {
                    damage = null
                    other.sendMessage("Your attack seems to have no effect...")
                }
            }
        }
    }
}
