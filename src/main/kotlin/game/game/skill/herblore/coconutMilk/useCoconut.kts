package game.skill.herblore.coconutMilk

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Replaces the item in slot [index] when it is still [id].
 */
fun replace(plr: Player, index: Int, id: Int, newId: Int) =
    if (plr.inventory[index]?.id == id) {
        plr.inventory.set(index, Item(newId))
        true
    } else {
        false
    }

/**
 * Returns the slot of [id] in this item-on-item event.
 */
fun ItemOnItemEvent.indexOf(id: Int) = if (usedItemId == id) usedItemIndex else targetItemIndex

// Break a coconut open with a hammer.
useItem(CoconutPotion.HAMMER).onItem(CoconutPotion.COCONUT) {
    if (replace(plr, indexOf(CoconutPotion.COCONUT), CoconutPotion.COCONUT, CoconutPotion.COCONUT_HALF)) {
        plr.sendMessage("You crush the coconut with a hammer.")
    }
}

// Pour the milk of a broken coconut into an empty vial.
useItem(CoconutPotion.VIAL).onItem(CoconutPotion.COCONUT_HALF) {
    val vial = indexOf(CoconutPotion.VIAL)
    val half = indexOf(CoconutPotion.COCONUT_HALF)
    if (plr.inventory[vial]?.id == CoconutPotion.VIAL &&
        replace(plr, half, CoconutPotion.COCONUT_HALF, CoconutPotion.COCONUT_SHELL)) {
        plr.inventory.set(vial, Item(CoconutPotion.COCONUT_MILK))
        plr.sendMessage("You fill the vial with coconut milk.")
    }
}

// Put an ingredient into coconut milk.
for (potion in CoconutPotion.entries) {
    useItem(CoconutPotion.COCONUT_MILK).onItem(potion.ingredient) {
        plr.overlays.open(object : MakeItemDialogue(potion.id) {
            override fun make(plr: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeCoconutPotionAction(plr, potion, forAmount))
        })
    }
}
