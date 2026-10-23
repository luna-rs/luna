package game.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.model.item.Item

/**
 * Assembles an oil lamp and oil-lantern frame into one oil lantern at Crafting level 26 for 50 XP.
 * Both components must be present before either is removed. ItemContainer.removeAll reports whether
 * any removal succeeded, so it cannot by itself validate the complete ingredient pair.
 * The conversion reduces occupied slots and uses the normal player item-on-item interaction.
 *
 * @author lare96
 */
useItem(4522).onItem(4540) {
    if (plr.crafting.level < 26) {
        plr.sendMessage("You need a Crafting level of 26 to combine these parts.")
    } else {
        val items = listOf(Item(4522), Item(4540))
        if (plr.inventory.containsAll(items) && plr.inventory.removeAll(items)) {
            plr.sendMessage("You combine the lamp and frame to make a lantern.")
            plr.crafting.addExperience(50.0)
            plr.inventory.add(Item(4535))
        }
    }
}