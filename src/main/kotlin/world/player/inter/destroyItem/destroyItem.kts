package world.player.inter.destroyItem

import api.predef.button
import api.predef.ext.get
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface

/**
 * Destroys the item if the dialogue is open.
 */
button(14175) {
    val inter = plr.interfaces.get(DestroyItemDialogueInterface::class)
    inter?.destroyItem(plr)
}

/**
 * Closes the interface.
 */
button(14176) { plr.interfaces.close() }