package engine.widget

import api.predef.*
import game.player.Messages
import game.player.Sounds
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent
import io.luna.game.model.EntityState
import io.luna.util.logging.LoggingSettings.FileOutputType
import org.apache.logging.log4j.util.Unbox.box

/**
 * An asynchronous logger that will handle item pickup logs.
 */
val logger = FileOutputType.ITEM_PICKUP.logger

/**
 * The `ITEM_PICKUP` logging level.
 */
val itemPickup = FileOutputType.ITEM_PICKUP.level

/**
 * Pickup the item.
 */
on(PickupItemEvent::class, EventPriority.HIGH) {
    val pickupItem = groundItem.toItem()
    when {
        !plr.inventory.hasSpaceFor(pickupItem) -> plr.sendMessage(Messages.INVENTORY_FULL)

        groundItem.state == EntityState.ACTIVE && world.items.unregister(groundItem) -> {
            plr.playSound(Sounds.PICKUP_ITEM)
            plr.inventory.add(pickupItem)
            logger.log(itemPickup, "{}: {}(x{})", plr.username, groundItem.def().name, box(groundItem.amount))
        }

        else -> plr.sendMessage("You were too late!")
    }
}