package world.player.skill.fletching.attachArrow

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Opens a [MakeItemDialogueInterface] for making arrows.
 */
fun openInterface(msg: ItemOnItemEvent, arrow: Arrow) {
    val interfaces = msg.plr.interfaces
    interfaces.open(object : MakeItemDialogueInterface(arrow.id) {
        override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(MakeArrowActionItem(plr, arrow, forAmount))
    })
}

// Intercept item on item event to open interface.
for (arrow in Arrow.VALUES) {
    useItem(Arrow.HEADLESS).onItem(arrow.tip) { openInterface(this, arrow) }
}

// Intercept specifically for HEADLESS_ARROW, because it differs from others.
Arrow.HEADLESS_ARROW.apply {
    useItem(tip).onItem(with) { openInterface(this, this@apply) }
}