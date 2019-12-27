package world.player.skill.crafting.armorCrafting

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * The armor that is displayed on the [SoftLeatherInterface].
 */
val armorList = listOf(HideArmor.LEATHER_BODY,
                       HideArmor.LEATHER_GLOVES,
                       HideArmor.LEATHER_BOOTS,
                       HideArmor.LEATHER_VAMBRACES,
                       HideArmor.LEATHER_CHAPS,
                       HideArmor.COIF,
                       HideArmor.LEATHER_COWL)

/**
 * Called when a button on the [SoftLeatherInterface] is clicked.
 */
fun craftArmor(plr: Player, armor: HideArmor, amount: Int) {
    if (plr.interfaces.isOpen(SoftLeatherInterface::class)) {
        plr.submitAction(CraftArmorAction(plr, armor, amount))
    }
}

// Register buttons for soft leather interface.
var buttonId = 8633
for (armor in armorList) {
    button(buttonId++) { craftArmor(plr, armor, 10) }
    button(buttonId++) { craftArmor(plr, armor, 5) }
    button(buttonId++) { craftArmor(plr, armor, 1) }
}
