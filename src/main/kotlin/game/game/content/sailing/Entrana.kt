package game.content.sailing

import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The search the Monks of Entrana make before sailing to Entrana. Weapons aren't allowed, and neither is gear with
 * attack or defence bonuses, apart from jewellery, ammunition and the clothing in [PERMITTED].
 *
 * @author TheLining
 */
object Entrana {

    /**
     * Gear with attack or defence bonuses, or worn as a weapon, that the monks still allow.
     */
    val PERMITTED: Set<Int> = setOf(
        577, 579, 581, 1017, 7390, 7392, 7394, 7396, // Wizard robes
        1033, 1035, // Zamorak monk robes
        4298, 4300, 4302, 4304, 4308, 4310, // H.A.M. robes
        3759, 3761, 3763, 3765, 3767, 3769, 3771, 3773, 3775, 3777, 3779, 3781, 3783, 3785, 3787, 3789, 3791, 3793,
        3795, 3797, 3799, // Fremennik clothing
        1059, 1580, 6110, // Leather, ice and ghostly gloves
        88, 89, 1061, 3105, 3107, 6106, 6145, // Boots of lightness, leather, climbing, spiked, ghostly, rock-shell
        1007, 1019, 1021, 1023, 1027, 1029, 1031, 6959, // Coloured capes
        2412, 2413, 2414, // God capes
        3840, 3842, 3844, // God books
        4565, 4566, 6541 // Easter basket, rubber chicken and mouse toy
    ) + (626..664 step 2) + // Gnome robes
        (2894..2942 step 2) + // Canifis robes
        (4315..4413 step 2) + // Team capes
        (2460..2476 step 2) // Flowers

    /**
     * Items that can't be worn but are still turned away: the dwarf multicannon's parts.
     */
    val FORBIDDEN = setOf(6, 8, 10, 12)

    /**
     * The jewellery and ammunition slots, which are never searched.
     */
    private val UNSEARCHED_SLOTS = setOf(Equipment.AMULET, Equipment.RING, Equipment.AMMUNITION)

    /**
     * The attack and defence bonuses.
     */
    private val COMBAT_BONUSES = EquipmentBonus.values().filter {
        it != EquipmentBonus.STRENGTH && it != EquipmentBonus.PRAYER
    }

    /**
     * Returns `true` if the monks won't allow [item] on Entrana. Noted items aren't searched.
     */
    fun isForbidden(item: Item): Boolean {
        if (item.id in FORBIDDEN) {
            return true
        }
        val def = item.equipDef ?: return false
        if (def.index in UNSEARCHED_SLOTS || item.id in PERMITTED) {
            return false
        }
        return def.index == Equipment.WEAPON || COMBAT_BONUSES.any { def.getBonus(it.index) != 0 }
    }

    /**
     * Returns `true` if [plr] is carrying or wearing anything the monks won't allow on Entrana.
     */
    fun isCarryingForbidden(plr: Player): Boolean =
        plr.inventory.any { it != null && isForbidden(it) } || plr.equipment.any { it != null && isForbidden(it) }
}
