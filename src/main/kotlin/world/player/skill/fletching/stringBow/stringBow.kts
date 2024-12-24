package world.player.skill.fletching.stringBow

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Opens a [MakeItemDialogueInterface] for stringing bows.
 */
fun openInterface(msg: ItemOnItemEvent, bow: Bow) {
    val interfaces = msg.plr.interfaces
    interfaces.open(object : MakeItemDialogueInterface(bow.strung) {
        override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
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