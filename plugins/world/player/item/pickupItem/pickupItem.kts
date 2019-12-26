package world.player.item.pickupItem

import api.predef.*
import io.luna.game.event.impl.PickupItemEvent
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player

// TODO A way to ensure this gets ran first?

/**
 * Check if there is space for the item in the inventory. If so, add it and remove it from the world.
 */
fun pickupItem(plr: Player, groundItem: GroundItem) {
    val item = groundItem.toItem()
    if (!plr.inventory.hasSpaceFor(item)) {
        plr.sendMessage("You do not have enough space in your inventory.")
        return
    }
    if (world.items.unregister(groundItem)) {
        plr.inventory.add(item)
    }
}

on(PickupItemEvent::class) { pickupItem(plr, item) }