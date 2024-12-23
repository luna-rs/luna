package world.player.skill.herblore.makePotion

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Opens a [MakeItemDialogueInterface] to make finished potions.
 */
fun makePotion(plr: Player, potion: FinishedPotion) {
    plr.interfaces.open(object : MakeItemDialogueInterface(potion.id) {
        override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(MakePotionActionItem(plr, potion, forAmount))
    })
}

// Start an action if we use the right items together.
FinishedPotion.ALL.forEach {
    useItem(it.unf).onItem(it.secondary) {
        makePotion(plr, it)
    }
}