package game.skill.fishing

import api.predef.*
import game.skill.fishing.catchFish.Tool
import game.skill.herblore.makePotion.FinishedPotion
import io.luna.game.model.item.Item

val rod = Tool.FISHING_ROD.id
val oil = FinishedPotion.BLAMISH_OIL.id

useItem(rod).onItem(oil) {
    val inventory = plr.inventory
    if (inventory[usedItemIndex]?.id != usedItemId || inventory[targetItemIndex]?.id != targetItemId) {
        return@onItem
    }
    val rodSlot = if (usedItemId == rod) usedItemIndex else targetItemIndex
    val oilSlot = if (usedItemId == oil) usedItemIndex else targetItemIndex
    inventory[rodSlot] = Item(Tool.LAVA_EEL_FISHING_ROD.id)
    inventory[oilSlot] = Item(229)
    plr.sendMessage("You rub the oil into the fishing rod.")
}
