package game.skill.fletching.cutLog

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Opens a [MakeItemDialogueInterface] for cutting logs.
 */
fun openInterface(plr: Player, log: Log) {
    plr.overlays.open(object : MakeItemDialogue(*log.unstrungIds) {
        override fun make(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(CutLogActionItem(plr, log.id, log.bows[index], forAmount))
    })
}

// Intercept item on item event to open interface.
for (log in Log.VALUES) {
    useItem(Log.KNIFE).onItem(log.id) { openInterface(plr, log) }
}