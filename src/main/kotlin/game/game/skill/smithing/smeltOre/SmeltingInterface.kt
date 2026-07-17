package game.skill.smithing.smeltOre

import game.skill.smithing.BarType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter

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