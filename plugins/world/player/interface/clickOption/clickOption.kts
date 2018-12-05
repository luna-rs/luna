import api.predef.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.OptionDialogueInterface

/**
 * Invoked when the player clicks an option on an option dialogue.
 */
fun clickOption(msg: ButtonClickEvent, option: Int) {
    val plr = msg.plr
    val inter = plr.getInterface(OptionDialogueInterface::class)
    if (inter != null) {
        when (option) {
            1 -> inter.firstOption(plr)
            2 -> inter.secondOption(plr)
            3 -> inter.thirdOption(plr)
            4 -> inter.fourthOption(plr)
            5 -> inter.fifthOption(plr)
            else -> throw IllegalArgumentException("'option' must be between 1-5 inclusive.")
        }

        if (inter.isOpen && !plr.dialogues.isPresent) {
            plr.closeInterfaces()
        } else {
            plr.advanceDialogues()
        }
    }
}

/**
 * The first option dialogue (2 options).
 */
button(14445) { clickOption(it, 1) }

button(14446) { clickOption(it, 2) }

button(2471) { clickOption(it, 1) }

button(2472) { clickOption(it, 2) }

button(2473) { clickOption(it, 3) }

/**
 * The third option dialogue (4 options).
 */
button(8209) { clickOption(it, 1) }

button(8210) { clickOption(it, 2) }

button(8211) { clickOption(it, 3) }

button(8212) { clickOption(it, 4) }

/**
 * The fourth option dialogue (5 options).
 */
button(8221) { clickOption(it, 1) }

button(8222) { clickOption(it, 2) }

button(8223) { clickOption(it, 3) }

button(8224) { clickOption(it, 4) }

button(8225) { clickOption(it, 5) }
