package engine.widget

import api.predef.*
import game.player.Messages
import game.player.Sound
import game.skill.runecrafting.essencePouch.EssencePouch
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.GroundItemClickEvent.PickupItemEvent
import io.luna.game.model.EntityState
import io.luna.game.model.mob.interact.InteractionPolicy
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
on(PickupItemEvent::class, EventPriority.HIGH, InteractionPolicy.EQUAL_POSITION_BIF) {
    val pickupItem = groundItem.toItem()
    val pouch = EssencePouch.ID_TO_POUCH[pickupItem.id]
    when {
        !plr.inventory.hasSpaceFor(pickupItem) -> plr.sendMessage(Messages.inventoryFull())

        pouch != null && pouch.isOwnedBy(plr) ->
            plr.sendMessage("You can only have one ${pouch.displayName} essence pouch at a time.")

        groundItem.state == EntityState.ACTIVE && world.items.unregister(groundItem) -> {
            plr.playSound(Sound.PICKUP_ITEM)
            plr.inventory.add(pickupItem)
            logger.log(itemPickup, "{}: {}(x{})", plr.username, groundItem.def().name, box(groundItem.amount))
        }

        else -> plr.sendMessage("You were too late!")
    }
}