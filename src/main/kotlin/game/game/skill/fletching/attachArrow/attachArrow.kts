package game.skill.fletching.attachArrow

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Opens a [MakeItemDialogue] for making arrows.
 */
fun openInterface(plr: Player, arrow: Arrow) {
    plr.overlays.open(object : MakeItemDialogue(arrow.id) {
        override fun make(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(MakeArrowActionItem(plr, arrow, forAmount))
    })
}

// Intercept item on item event to open interface.
for (arrow in Arrow.VALUES) {
    useItem(Arrow.HEADLESS).onItem(arrow.tip) { openInterface(plr, arrow) }
}

// Intercept specifically for HEADLESS_ARROW, because it differs from others.
Arrow.HEADLESS_ARROW.apply {
    useItem(tip).onItem(with) { openInterface(plr, this@apply) }
}