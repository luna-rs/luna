import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * A [DialogueInterface] that opens the cook food dialogue.
 */
class CookingInterface(val food: Food, val usingFire: Boolean = false) : DialogueInterface(1743) {

    override fun init(plr: Player): Boolean {
        val cooked = food.cooked
        plr.queue(WidgetItemModelMessageWriter(13716, 190, cooked))
        plr.sendText(itemDef(cooked).name, 13717)
        return true
    }
}
