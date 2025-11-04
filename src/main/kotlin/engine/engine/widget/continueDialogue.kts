package engine.widget

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.ContinueDialogueEvent

/**
 * Advances the current dialogue or closes the current interface.
 */
on(ContinueDialogueEvent::class, EventPriority.HIGH) {
    if (plr.dialogues != null) {
        plr.dialogues.advance()
    } else {
        plr.overlays.closeWindows()
    }
}