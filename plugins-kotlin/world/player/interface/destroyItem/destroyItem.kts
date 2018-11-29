import api.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface

/**
 * Destroys the item if the dialogue is open.
 */
on(ButtonClickEvent::class)
    .args(14175)
    .run {
        val plr = it.plr
        plr.interfaces.get(DestroyItemDialogueInterface::class)?.destroyItem(plr)
        plr.interfaces.close()
    }

/**
 * Closes the interface.
 */
on(ButtonClickEvent::class)
    .args(14176)
    .run { it.plr.interfaces.close() }