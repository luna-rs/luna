package world.player.inter.makeItem

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

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
fun makeItem(msg: ButtonClickEvent, inter: MakeItemDialogueInterface, action: MakeItemOption) {

    // Because 1 and 3 use the same interface, but different indexes.
    fun checkLength() {
        when (inter.length) {
            1 -> action.index = 0
            3 -> action.index = 1
        }
    }

    when (msg.id) {
        8893, 8892, 8891, 8890 -> checkLength()
    }
    action.run(msg.plr, inter)
}

/**
 * Listens for button clicks on the [MakeItemDialogueInterface].
 */
on(ButtonClickEvent::class)
    .filter { plr.interfaces.isOpen(MakeItemDialogueInterface::class) }
    .then {
        val action = buttonMap[id]
        if (action != null) {
            val inter = plr.interfaces.get(MakeItemDialogueInterface::class)!!
            makeItem(this, inter, action)
        }
    }