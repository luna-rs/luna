package world.player.skill.fletching.cutLog

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Opens a [MakeItemDialogueInterface] for cutting logs.
 */
fun openInterface(msg: ItemOnItemEvent, log: Log) {
    val interfaces = msg.plr.interfaces
    interfaces.open(object : MakeItemDialogueInterface(*log.unstrungIds) {
        override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(CutLogActionItem(plr, log.id, log.bows[index], forAmount))
    })
}

// Intercept item on item event to open interface.
for (log in Log.VALUES) {
    useItem(Log.KNIFE).onItem(log.id) { openInterface(this, log) }
}