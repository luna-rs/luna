package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.Luna
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.AmmoType

if (Luna.settings().skills().slayerEquipmentNeeded()) {
    val TUROTH = setOf(1626, 1627, 1628, 1629, 1630, 1631, 1632)
    val BASILISK = setOf(1616, 1617)
    val KURASK = setOf(1608, 1609)

    val ALLOWED_WEAPONS = setOf(
        4158, // Leaf-bladed spear
    )

    fun usingBroadArrows(plr: Player) = // TODO Implement broad arrow data.
        plr.combat.ranged.ammo.type == AmmoType.BROAD_ARROW && plr.combat.weapon.isRanged

    for (id in TUROTH + BASILISK + KURASK) {
        combat(id) {
            defend {
                if (other is Player && other.equipment.weapon?.id !in ALLOWED_WEAPONS && !usingBroadArrows(other)) {
                    damage = null
                    other.sendMessage("Your attack seems to have no effect...")
                }
            }
        }
    }
}