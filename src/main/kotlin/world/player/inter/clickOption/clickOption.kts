package world.player.inter.clickOption

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.OptionDialogueInterface
import io.luna.game.model.mob.inter.StandardInterface

/**
 * Invoked when the player clicks an option on an option dialogue.
 */
fun clickOption(msg: ButtonClickEvent, option: Int) {
    val plr = msg.plr
    val inter = plr.interfaces.get(OptionDialogueInterface::class)

    if (inter != null) {
        when (option) {
            1 -> inter.firstOption(plr)
            2 -> inter.secondOption(plr)
            3 -> inter.thirdOption(plr)
            4 -> inter.fourthOption(plr)
            5 -> inter.fifthOption(plr)
            else -> throw IllegalArgumentException("'option' must be between 1-5 inclusive.")
        }

        // Only close if we still have the same interface open.
        if (plr.dialogues.isEmpty && inter.isOpen && !plr.interfaces.isInputOpen) {
            plr.interfaces.close()
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
