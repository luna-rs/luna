package game.item.degradable

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.def.DegradableItemDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.IndexedItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.net.msg.out.WidgetIndexedItemsMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * Warning dialogue shown when the player attempts to drop a degradable item that should fully degrade on drop.
 *
 * The dialogue previews the item, asks the player for confirmation, and explains that the dropped result will be the
 * fully degraded form rather than the currently equipped or held state.
 *
 * @param index The inventory index of the item.
 * @param itemId The item being confirmed for dropping.
 * @author lare96
 */
class DegradableDropWarningDialogue(val index: Int, val itemId: Int) : DialogueInterface(14170) {

    override fun init(plr: Player): Boolean {
        // Send packets that build the interface.

        val item = IndexedItem(0, itemId, 1)
        plr.queue(WidgetIndexedItemsMessageWriter(14171, item))

        plr.queue(WidgetTextMessageWriter("Are you sure you want to drop this item?", 14174))
        plr.queue(WidgetTextMessageWriter("Yes", 14175))
        plr.queue(WidgetTextMessageWriter("No", 14176))
        plr.queue(WidgetTextMessageWriter("This will degrade completely", 14182))
        plr.queue(WidgetTextMessageWriter("after being dropped.", 14183))
        plr.queue(WidgetTextMessageWriter(ItemDefinition.ALL.retrieve(itemId).name, 14184))
        return true
    }

    /**
     * Drops the fully degraded form of the dialogue's item for the player.
     *
     * This walks the degradable item chain starting from [itemId] until it reaches the last defined degradable
     * stage, closes the player's open windows, and spawns that final item on the ground at the player's position.
     *
     * @param plr The player dropping the item.
     */
    fun dropItem(plr: Player) {
        var def = DegradableItemDefinition.ALL[itemId].orElse(null)
        var dropItemId = itemId
        while (def != null) {
            dropItemId = def.id
            def = DegradableItemDefinition.ALL[def.nextId].orElse(null)
        }
        plr.overlays.closeWindows()
        plr.inventory[index] = null
        world.addItem(dropItemId, 1, plr.position, plr)
    }
}