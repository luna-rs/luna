package engine.widget

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.dialogue.DestroyItemDialogue

/**
 * Destroys the item if the dialogue is open.
 */
button(14175) {
    plr.overlays[DestroyItemDialogue::class]?.destroyItem(plr)
}

/**
 * Closes the interface.
 */
button(14176) { plr.overlays.closeWindows() }