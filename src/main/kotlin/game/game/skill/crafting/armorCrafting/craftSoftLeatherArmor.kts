package game.skill.crafting.armorCrafting

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player


/**
 * Called when a button on the [SoftLeatherInterface] is clicked.
 */
fun craftArmor(plr: Player, armor: HideArmor, amount: Int) {
    if (SoftLeatherInterface::class in plr.overlays) {
        plr.submitAction(CraftArmorActionItem(plr, armor, amount))
    }
}

// Register buttons for soft leather interface.
for (entry in SoftLeatherInterface.BUTTON_MAP.entries) {
    val (ten, five, one) = entry.value
    button(ten) { craftArmor(plr, entry.key, 10) }
    button(five) { craftArmor(plr, entry.key, 5) }
    button(one) { craftArmor(plr, entry.key, 1) }
}