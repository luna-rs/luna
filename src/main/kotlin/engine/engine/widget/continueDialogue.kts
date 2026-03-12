package engine.widget

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.ContinueDialogueEvent
import io.luna.game.model.mob.dialogue.DialogueInterface

/**
 * Advances the current dialogue or closes the current interface.
 */
on(ContinueDialogueEvent::class, EventPriority.HIGH) {
    plr.overlays[DialogueInterface::class]?.isContinueClicked = true
    if (plr.dialogues != null) {
        plr.dialogues.advance()
    } else {
        plr.overlays.closeWindows()
    }
}