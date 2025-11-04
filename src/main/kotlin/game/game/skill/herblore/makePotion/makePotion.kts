package game.skill.herblore.makePotion

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Opens a [MakeItemDialogueInterface] to make finished potions.
 */
fun makePotion(plr: Player, potion: FinishedPotion) {
    plr.overlays.open(object : MakeItemDialogue(potion.id) {
        override fun make(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(MakePotionActionItem(plr, potion, forAmount))
    })
}

// Start an action if we use the right items together.
FinishedPotion.ALL.forEach {
    useItem(it.unf).onItem(it.secondary) {
        makePotion(plr, it)
    }
}