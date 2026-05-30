package game.skill.crafting.armorCrafting

import game.skill.crafting.armorCrafting.SoftLeatherInterface.Companion.ARMOR_LIST
import game.skill.crafting.armorCrafting.SoftLeatherInterface.Companion.BUTTON_MAP
import io.luna.game.model.mob.overlay.StandardInterface

/**
 * The soft leather crafting interface.
 *
 * This interface is opened when a player uses soft leather with a needle and is shown the available soft leather armour
 * choices.
 *
 * @author lare96
 */
class SoftLeatherInterface : StandardInterface(2311) {

    companion object {

        /**
         * The soft leather armour entries shown on this interface.
         *
         * The order of this list must match the button order used by the client interface, because [BUTTON_MAP] assigns
         * button ids by walking this list in sequence.
         */
        val ARMOR_LIST = listOf(
            HideArmor.LEATHER_BODY,
            HideArmor.LEATHER_GLOVES,
            HideArmor.LEATHER_BOOTS,
            HideArmor.LEATHER_VAMBRACES,
            HideArmor.LEATHER_CHAPS,
            HideArmor.COIF,
            HideArmor.LEATHER_COWL
        )

        /**
         * Maps each soft leather armour option to its three quantity button ids.
         *
         * The button ids are generated from the first soft leather crafting button and assigned in groups of three for
         * each armour entry in [ARMOR_LIST].
         */
        val BUTTON_MAP: Map<HideArmor, Triple<Int, Int, Int>> = run {
            val map = HashMap<HideArmor, Triple<Int, Int, Int>>()
            var buttonId = 8633
            for (armor in SoftLeatherInterface.ARMOR_LIST) {
                map[armor] = Triple(buttonId++, buttonId++, buttonId++)
            }
            map
        }
    }
}