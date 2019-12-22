package world.player.item.dropItem

import api.predef.*
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DestroyItemDialogueInterface
import io.luna.util.LoggingSettings.FileOutputType
import org.apache.logging.log4j.util.Unbox.box
import java.util.*

// TODO A system to ensure this plugin is always ran first?

/**
 * An asynchronous logger that will handle item drop logs.
 */
val logger = FileOutputType.ITEM_DROP.logger

/**
 * The `ITEM_DROP` logging level.
 */
val ITEM_DROP = FileOutputType.ITEM_DROP.level

/**
 * Check if the item is in the inventory, if so drop it. Items that are not tradeable or that do not have a "Drop"
 * context menu action, open the "Destroy item" confirmation.
 */
fun dropItem(plr: Player, index: Int, id: Int) {
    val item = plr.inventory[index]
    if (item == null || item.id != id) {
        return
    }

    val itemDef = item.itemDef
    if (itemDef.isTradeable && !itemDef.inventoryActions.contains("Destroy")) {
        val groundItem = GroundItem(plr.context, item.id, item.amount, plr.position, Optional.of(plr))
        if (world.items.register(groundItem)) {
            plr.inventory[index] = null
        } else {
            plr.sendMessage("You cannot drop this here.")
        }
    } else {
        plr.interfaces.open(DestroyItemDialogueInterface(index, id))
    }
    logger.log(ITEM_DROP, "{}: {}(x{})", plr.username, item.itemDef.name, box(item.amount))
}


on(DropItemEvent::class)
    .filter { widgetId == 3214 }
    .then { dropItem(plr, index, itemId) }