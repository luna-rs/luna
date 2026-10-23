package game.item.teleport

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * The item box shown once Eluned has re-enchanted a Tiny elf crystal. Unlike a give-item dialogue, it only shows the
 * crystal; the inventory is changed before it opens.
 *
 * @author TheLining
 */
class TeleportCrystalChantDialogue : DialogueInterface(306) {

    override fun init(player: Player): Boolean {
        player.sendText("Although you can't hear anything, your crystals have\\nbeen chanted.", 308)
        player.queue(WidgetItemModelMessageWriter(307, 200, TeleportCrystal.RECHARGED))
        return true
    }
}
