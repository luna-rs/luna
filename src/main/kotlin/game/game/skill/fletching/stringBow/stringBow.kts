package game.skill.fletching.stringBow

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Opens a [MakeItemDialogueInterface] for stringing bows.
 */
fun openInterface(msg: ItemOnItemEvent, bow: Bow) {
    val interfaces = msg.plr.overlays
    interfaces.open(object : MakeItemDialogue(bow.strung) {
        override fun make(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(StringBowActionItem(plr, bow, forAmount))
    })
}

// Intercept item on item event to open interface.
for (bow in Bow.VALUES) {
    if (bow == Bow.ARROW_SHAFT) {
        continue
    }
    useItem(Bow.BOW_STRING).onItem(bow.unstrung) { openInterface(this, bow) }
}