package engine.widget

import api.predef.*
import game.content.mageArena.MageArena
import game.content.treasureTrails.TreasureTrails
import game.player.Messages
import game.player.Sound
import game.obj.resource.ZamorakWine
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
        pickupItem.id in MageArena.capes && !MageArena.canTakeCape(plr) -> {
            plr.sendMessage("You may only possess one sacred cape at a time.")
            plr.sendMessage("The conflicting powers of the capes drive them apart.")
        }
        !TreasureTrails.canAcquire(plr, pickupItem.id) -> Unit
        ZamorakWine.onPickup(plr, groundItem) -> Unit

        !plr.inventory.hasSpaceFor(pickupItem) -> plr.sendMessage(Messages.inventoryFull())

        pouch != null && pouch.isOwnedBy(plr) ->
            plr.sendMessage("You can only have one ${pouch.displayName} essence pouch at a time.")

        groundItem.state == EntityState.ACTIVE && world.items.unregister(groundItem) -> {
            plr.playSound(Sound.PICKUP_ITEM)
            plr.inventory.add(pickupItem)
            TreasureTrails.acquired(plr, pickupItem.id)
            logger.log(itemPickup, "{}: {}(x{})", plr.username, groundItem.def().name, box(groundItem.amount))
        }

        else -> plr.sendMessage("You were too late!")
    }
}
