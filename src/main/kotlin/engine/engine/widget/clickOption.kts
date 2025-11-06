package engine.widget

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.overlay.OverlayType

/**
 * Invoked when the player clicks an option on an option dialogue.
 */
fun clickOption(msg: ButtonClickEvent, option: Int) {
    val plr = msg.plr
    val inter = plr.overlays[OptionDialogue::class]

    if (inter != null) {
        when (option) {
            1 -> inter.first(plr)
            2 -> inter.second(plr)
            3 -> inter.third(plr)
            4 -> inter.fourth(plr)
            5 -> inter.fifth(plr)
            else -> throw IllegalArgumentException("'option' must be between 1-5 inclusive.")
        }

        // Only close if we still have the same interface open.
        if (plr.dialogues == null && inter.isOpen && !plr.overlays.containsType(OverlayType.INPUT)) {
            plr.overlays.closeWindows()
        }
    }
}

/**
 * The first option dialogue (2 options).
 */
button(14445) { clickOption(this, 1) }

button(14446) { clickOption(this, 2) }

button(2471) { clickOption(this, 1) }

button(2472) { clickOption(this, 2) }

button(2473) { clickOption(this, 3) }

/**
 * The third option dialogue (4 options).
 */
button(8209) { clickOption(this, 1) }

button(8210) { clickOption(this, 2) }

button(8211) { clickOption(this, 3) }

button(8212) { clickOption(this, 4) }

/**
 * The fourth option dialogue (5 options).
 */
button(8221) { clickOption(this, 1) }

button(8222) { clickOption(this, 2) }

button(8223) { clickOption(this, 3) }

button(8224) { clickOption(this, 4) }

button(8225) { clickOption(this, 5) }
