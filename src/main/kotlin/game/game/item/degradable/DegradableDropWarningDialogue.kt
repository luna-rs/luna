package game.item.degradable

import api.predef.*
import api.predef.ext.*
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
     * Removes the item from the player's inventory and drops the result of its final degradation stage.
     *
     * The item's degradable set is resolved from [itemId], and the last entry in that chain is used to determine what
     * should happen when the item is dropped. Any open windows are closed before the inventory slot at [index] is
     * cleared.
     *
     * If the final degradable stage has a valid [DegradableItem.nextId], that item is spawned on the ground at the
     * player's position and the player is informed that the item degraded completely on drop. Otherwise, no ground
     * item is created and the player is informed that the item disappeared entirely.
     *
     * @param plr The player dropping the degradable item.
     */
    fun dropItem(plr: Player) {
        val (set, _) = DegradableEquipmentHandler.getDegradable(itemId) ?: return
        val lastItem = set.items.last()

        plr.overlays.closeWindows()
        plr.inventory[index] = null
        if (lastItem.nextId > 0) {
            world.addItem(lastItem.nextId, 1, plr.position, plr)
            plr.sendMessage("The item degrades completely as it touches the ground!")
        } else {
            plr.sendMessage("The item disappears as it touches the ground!")
        }
        return
    }
}