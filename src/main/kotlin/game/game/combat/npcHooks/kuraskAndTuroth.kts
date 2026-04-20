package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.AmmoType

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
     * Determines whether the player is attacking with broad arrows from a ranged weapon.
     *
     * This is used as part of the Turoth and Kurask equipment restriction check.
     *
     * @param plr The attacking player.
     * @return `true` if the player is using a ranged weapon with broad arrows, otherwise `false`.
     */
    fun usingBroadArrows(plr: Player): Boolean =
        // TODO Implement broad arrow data.
        plr.combat.ranged.ammo.type == AmmoType.BROAD_ARROW && plr.combat.weapon.isRanged

    for (id in TUROTH + KURASK) {
        /*
         * When slayer equipment restrictions are enabled, players must use either a leaf-bladed spear or broad arrows
         * from a ranged weapon to deal damage. Any other attack is negated.
         */
        combat(id) {
            defend {
                if (other is Player && other.equipment.weapon?.id != LEAF_BLADED_SPEAR &&
                    !usingBroadArrows(other)) {
                    damage = null
                    other.sendMessage("Your attack seems to have no effect...")
                }
            }
        }
    }
}