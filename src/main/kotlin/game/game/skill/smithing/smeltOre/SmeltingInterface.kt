package game.skill.smithing.smeltOre

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import game.skill.smithing.BarType

/**
 * A [DialogueInterface] representing the smelting interface.
 *
 * @author lare96
 */
class SmeltingInterface : DialogueInterface(2400) {

    override fun init(player: Player): Boolean {
        for(bar in BarType.VALUES) {
            player.queue(WidgetItemModelMessageWriter(bar.widget, 150, bar.id))
        }
        return true
    }
}