package world.player.skill.herblore.makeUnfPotion

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Opens a [MakeItemDialogueInterface] to make unfinished potions.
 */
fun makeUnf(msg: ItemOnItemEvent, herb: Int) {
    val plr = msg.plr
    val unfPotion = UnfPotion.HERB_TO_UNF[herb]
    if (unfPotion != null) {
        plr.interfaces.open(object : MakeItemDialogueInterface(unfPotion.id) {
            override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeUnfAction(plr, unfPotion, forAmount))
        })
    }
}

/**
 * Intercept event to make unf. potions if the required items are present.
 */
on(ItemOnItemEvent::class) {
    when (UnfPotion.VIAL_OF_WATER) {
        targetId -> makeUnf(this, usedId)
        usedId -> makeUnf(this, targetId)
    }
}
