package world.player.skill.cooking.cookFood

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.DialogueInterface
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * A [DialogueInterface] that opens the cook food dialogue.
 */
class CookingInterface(val food: Food, val usingFire: Boolean, val cookObj: GameObject) : DialogueInterface(1743) {

    override fun init(plr: Player): Boolean {
        val cooked = food.cooked
        plr.queue(WidgetItemModelMessageWriter(13716, 190, cooked))
        plr.queue(WidgetTextMessageWriter(itemName(cooked), 13717))
        return true
    }
}
