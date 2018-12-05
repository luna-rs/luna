import api.predef.*
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface

/**
 * Destroys the item if the dialogue is open.
 */
button(14175) {
    val plr = it.plr
    plr.getInterface(DestroyItemDialogueInterface::class)?.destroyItem(plr)
}

/**
 * Closes the interface.
 */
button(14176) { it.plr.closeInterfaces() }