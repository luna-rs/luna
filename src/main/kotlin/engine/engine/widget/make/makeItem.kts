package engine.widget.make

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * A list of button ids and the indexes that they correspond to.
 */
val buttonList = listOf(
    ButtonIndex(make1 = 8893, index = 0),
    ButtonIndex(make1 = 8874, index = 0),
    ButtonIndex(make1 = 8878, index = 1),
    ButtonIndex(make1 = 8889, index = 0),
    ButtonIndex(make1 = 8893, index = 1),
    ButtonIndex(make1 = 8897, index = 2),
    ButtonIndex(make1 = 8909, index = 0),
    ButtonIndex(make1 = 8913, index = 1),
    ButtonIndex(make1 = 8917, index = 2),
    ButtonIndex(make1 = 8921, index = 3),
    ButtonIndex(make1 = 8949, index = 0),
    ButtonIndex(make1 = 8953, index = 1),
    ButtonIndex(make1 = 8957, index = 2),
    ButtonIndex(make1 = 8961, index = 3),
    ButtonIndex(make1 = 8965, index = 4))

/**
 * A mapping of every option's button to its [MakeItemOption].
 */
val buttonMap = buttonList.flatMap { it.options() }.toMap()

/**
 * Runs the [MakeItemOption] after checking the item length.
 */
fun makeItem(msg: ButtonClickEvent, inter: MakeItemDialogue, action: MakeItemOption) {

    // One- and three-item dialogues share buttons. Resolve a fresh action for this click:
    // Make X opens a delayed callback, so another player's click must not change its index.
    val index = when (msg.id) {
        8893, 8892, 8891, 8890 -> when (inter.length) {
            1 -> 0
            3 -> 1
            else -> action.index
        }
        else -> action.index
    }
    if (index !in 0 until inter.length) return
    MakeItemOption(action.amount, index).run(msg.plr, inter)
}

/**
 * Listens for button clicks on the [MakeItemDialogue].
 */
on(ButtonClickEvent::class)
    .filter { MakeItemDialogue::class in plr.overlays }
    .then {
        val action = buttonMap[id]
        if (action != null) {
            val dialogue = plr.overlays[MakeItemDialogue::class]
            if (dialogue != null) {
                makeItem(this, dialogue, action)
            }
        }
    }