package world.player.skill.magic.teleOther

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * A [StandardInterface] representing the teleother confirmation screen.
 */
class TeleOtherInterface(val source: Player, val target: Player, val type: TeleOtherType) :
    StandardInterface(12468) {

    override fun onOpen(player: Player) {
        player.queue(WidgetTextMessageWriter(source.username, 12558))
        player.queue(WidgetTextMessageWriter(type.name, 12560))
    }
}