package world.player.skill.fletching.cutLog

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface


/**
 * Opens a [MakeItemDialogueInterface] for cutting logs.
 */
fun openInterface(msg: ItemOnItemEvent, id: Int) {
    val log = Log.ID_TO_LOG[id]
    if (log != null) {
        val interfaces = msg.plr.interfaces
        interfaces.open(object : MakeItemDialogueInterface(*log.unstrungIds) {
            override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(CutLogAction(plr, log.id, log.bows[index], forAmount))
        })
    }
}

// Intercept item on item event to open interface.
on(ItemOnItemEvent::class) {
    when (Log.KNIFE) {
        targetId -> openInterface(this, usedId)
        usedId -> openInterface(this, targetId)
    }
}