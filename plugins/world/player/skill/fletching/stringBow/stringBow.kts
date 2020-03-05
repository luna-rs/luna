package world.player.skill.fletching.stringBow

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Opens a [MakeItemDialogueInterface] for stringing bows.
 */
fun openInterface(msg: ItemOnItemEvent, id: Int) {
    val bow = Bow.UNSTRUNG_TO_BOW[id]
    if (bow != null && bow != Bow.ARROW_SHAFT) {
        val interfaces = msg.plr.interfaces
        interfaces.open(object : MakeItemDialogueInterface(bow.strung) {
            override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(StringBowAction(plr, bow, forAmount))
        })
    }
}

// Intercept item on item event to open interface.
on(ItemOnItemEvent::class) {
    when (Bow.BOW_STRING) {
        targetId -> openInterface(this, usedId)
        usedId -> openInterface(this, targetId)
    }
}