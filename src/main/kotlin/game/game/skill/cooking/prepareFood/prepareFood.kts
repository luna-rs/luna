package game.skill.cooking.prepareFood

import api.predef.*
import io.luna.game.model.mob.Player
import game.skill.cooking.cookFood.MakeWineActionItem
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Opens the interface for making dough.
 */
fun openDough(plr: Player, removeIds: MutableSet<Int>) {
    plr.overlays.open(object : MakeItemDialogue(*IncompleteFood.DOUGH.keys.toIntArray()) {
        override fun make(player: Player?, id: Int, index: Int, forAmount: Int) {
            val dough = IncompleteFood.DOUGH[id]
            if (dough != null) {
                plr.submitAction(PrepareFoodActionItem(plr, dough, removeIds, forAmount))
            }
        }
    })
}

/**
 * Opens the interface for making other [IncompleteFood] types.
 */
fun openOther(plr: Player, food: IncompleteFood, removeIds: MutableSet<Int>) {
    plr.overlays.open(object : MakeItemDialogue(food.id) {
        override fun make(player: Player?, id: Int, index: Int, forAmount: Int) {
            if (food == IncompleteFood.UNFERMENTED_WINE) {
                plr.submitAction(MakeWineActionItem(plr, forAmount))
            } else {
                plr.submitAction(PrepareFoodActionItem(plr, food, removeIds, forAmount))
            }
        }


    })
}

// Loop through all incomplete foods and register listeners.
IncompleteFood.ALL.values.forEach {
    for (id in it.otherIngredients) {
        useItem(it.baseIngredient).onItem(id) {
            val itemSet = mutableSetOf(usedItemId, targetItemId)
            if (IncompleteFood.DOUGH.containsKey(it.id)) {
                // Open all dough on one interface.
                openDough(plr, itemSet)
            } else {
                openOther(plr, it, itemSet)
            }
        }
    }
}
