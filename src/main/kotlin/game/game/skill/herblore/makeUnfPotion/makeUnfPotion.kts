package game.skill.herblore.makeUnfPotion

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Opens a [MakeItemDialogueInterface] to make unfinished potions.
 */
fun makeUnf(msg: ItemOnItemEvent, unfPotion: UnfPotion) {
    val plr = msg.plr
    plr.overlays.open(object : MakeItemDialogue(unfPotion.id) {
        override fun make(player: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(MakeUnfActionItem(plr, unfPotion, forAmount))
    })
}

// Intercept events to make unf. potions if the required items are present.
UnfPotion.HERB_TO_UNF.entries.forEach {
    useItem(UnfPotion.VIAL_OF_WATER).onItem(it.key) {
        makeUnf(this, it.value)
    }
}
