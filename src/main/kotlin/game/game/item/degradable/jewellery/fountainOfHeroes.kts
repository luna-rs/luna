package game.item.degradable.jewellery

import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * The Fountain of Heroes object id.
 */
val FOUNTAIN_OF_HEROES = 2638

/**
 * The amulets that can be recharged at the fountain: uncharged, and glory (1) to (3).
 */
val RECHARGEABLE_GLORIES = listOf(1704, 1706, 1708, 1710)

/**
 * A chatbox box that shows an item next to some text, without giving the item.
 */
class GloryChargedDialogue(private val itemId: Int, private val text: String) : DialogueInterface(306) {
    override fun init(plr: Player): Boolean {
        plr.sendText(text, 308)
        plr.queue(WidgetItemModelMessageWriter(307, 200, itemId))
        return true
    }
}

for (id in RECHARGEABLE_GLORIES) {
    useItem(id).onObject(FOUNTAIN_OF_HEROES) {
        if (plr.inventory[usedItemIndex]?.id != usedItemId) {
            return@onObject
        }
        val charged = Item(TeleportJewellery.AMULET_OF_GLORY.items.first())
        plr.inventory[usedItemIndex] = charged
        plr.sendMessage("You dip the amulet in the fountain...you feel a power emanating from it.")
        plr.sendMessage("You can now rub the amulet to teleport and wear it to get more gems whilst mining.")
        plr.overlays.open(GloryChargedDialogue(charged.id,
                                               "You dip the amulet in the fountain...you feel a power\\nemanating from it.\\n" +
                                               "@dbl@Effect:@bla@ You can now rub the amulet to teleport and\\nwear it to get more gems whilst mining."))
    }
}
