package game.skill.herblore.grindIngredient

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue


/**
 * Opens a [MakeItemDialogueInterface] for grinding ingredients.
 */
fun grind(msg: ItemOnItemEvent, ingredient: Ingredient) {
    val interfaces = msg.plr.overlays
    interfaces.open(object : MakeItemDialogue(ingredient.newId) {
        override fun make(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(GrindActionItem(plr, ingredient, forAmount))
    })
}

// Intercept event for using ingredient with pestle and mortar.
Ingredient.OLD_TO_INGREDIENT.values.forEach {
    useItem(Ingredient.PESTLE_AND_MORTAR).onItem(it.id) { grind(this, it) }
}