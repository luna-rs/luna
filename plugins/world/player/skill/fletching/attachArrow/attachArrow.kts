package world.player.skill.fletching.attachArrow

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Opens a [MakeItemDialogueInterface] for making arrows.
 */
fun openInterface(msg: ItemOnItemEvent, arrow: Arrow?) {
    if (arrow != null) {
        val interfaces = msg.plr.interfaces
        interfaces.open(object : MakeItemDialogueInterface(arrow.id) {
            override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeArrowAction(plr, arrow, forAmount))
        })
    }
}

/**
 * Intercept item on item event to open interface.
 */
on(ItemOnItemEvent::class) {
    when (Arrow.HEADLESS) {
        targetId -> openInterface(this, Arrow.TIP_TO_ARROW[usedId])
        usedId -> openInterface(this, Arrow.TIP_TO_ARROW[targetId])
    }
}

/**
 * Intercept even specifically for [Arrow.HEADLESS_ARROW], because it differs from others.
 */
Arrow.HEADLESS_ARROW.apply {
    on(ItemOnItemEvent::class)
        .filter { matches(tip, with) }
        .then { openInterface(this, this@apply) }
}