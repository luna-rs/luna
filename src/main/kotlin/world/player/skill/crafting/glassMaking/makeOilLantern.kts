package world.player.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.model.item.Item

// Use oil lamp on empty frame.
useItem(4522).onItem(4540) {
    if (plr.crafting.level < 26) {
        plr.sendMessage("You need a Crafting level of 26 to combine these parts.")
    } else {
        val items = listOf(Item(4522), Item(4540))
        if (plr.inventory.removeAll(items)) {
            plr.sendMessage("You combine the lamp and frame to make a lantern.")
            plr.crafting.addExperience(50.0)
            plr.inventory.add(Item(4535))
        }
    }
}