package world.player.skill.herblore.grindIngredient

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface


/**
 * Opens a [MakeItemDialogueInterface] for grinding ingredients.
 */
fun grind(msg: ItemOnItemEvent, id: Int) {
    val ingredient = Ingredient.OLD_TO_INGREDIENT[id]
    if (ingredient != null) {
        val interfaces = msg.plr.interfaces
        interfaces.open(object : MakeItemDialogueInterface(ingredient.newId) {
            override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(GrindAction(plr, ingredient, forAmount))
        })
    }
}

/**
 * Intercept event for using ingredient with pestle and mortar.
 */
on(ItemOnItemEvent::class) {
    when (Ingredient.PESTLE_AND_MORTAR) {
        usedId -> grind(this, targetId)
        targetId -> grind(this, usedId)
    }
}
