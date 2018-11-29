import api.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.OptionDialogueInterface

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
            else -> throw Exception("Option value must be between 1-5 inclusive.")
        }

        if (inter.isOpen && !plr.dialogues.isPresent) {
            plr.interfaces.close()
        } else {
            plr.advanceDialogues()
        }
    }
}

/**
 * The first option dialogue (2 options).
 */
on(ButtonClickEvent::class)
    .args(14445)
    .run { clickOption(it, 1) }

on(ButtonClickEvent::class)
    .args(14446)
    .run { clickOption(it, 2) }

/**
 * The second option dialogue (3 options).
 */
on(ButtonClickEvent::class)
    .args(2471)
    .run { clickOption(it, 1) }

on(ButtonClickEvent::class)
    .args(2472)
    .run { clickOption(it, 2) }

on(ButtonClickEvent::class)
    .args(2473)
    .run { clickOption(it, 3) }

/**
 * The third option dialogue (4 options).
 */
on(ButtonClickEvent::class)
    .args(8209)
    .run { clickOption(it, 1) }

on(ButtonClickEvent::class)
    .args(8210)
    .run { clickOption(it, 2) }

on(ButtonClickEvent::class)
    .args(8211)
    .run { clickOption(it, 3) }

on(ButtonClickEvent::class)
    .args(8212)
    .run { clickOption(it, 4) }

/**
 * The fourth option dialogue (5 options).
 */
on(ButtonClickEvent::class)
    .args(8221)
    .run { clickOption(it, 1) }

on(ButtonClickEvent::class)
    .args(8222)
    .run { clickOption(it, 2) }

on(ButtonClickEvent::class)
    .args(8223)
    .run { clickOption(it, 3) }

on(ButtonClickEvent::class)
    .args(8224)
    .run { clickOption(it, 4) }

on(ButtonClickEvent::class)
    .args(8225)
    .run { clickOption(it, 5) }
