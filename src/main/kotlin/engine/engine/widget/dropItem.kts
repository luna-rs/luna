package engine.widget

import api.predef.*
import game.player.Sounds
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.dialogue.DestroyItemDialogue
import io.luna.util.logging.LoggingSettings.FileOutputType
import org.apache.logging.log4j.util.Unbox.box

/**
 * An asynchronous logger that will handle item drop logs.
 */
val logger = FileOutputType.ITEM_DROP.logger

/**
 * The logging level.
 */
val itemDrop = FileOutputType.ITEM_DROP.level

/**
 * Drop the item.
 */
on(DropItemEvent::class, EventPriority.HIGH) {
    val item = plr.inventory[index]
    val itemDef = item.itemDef
    if (itemDef.isTradeable && !itemDef.inventoryActions.contains("Destroy")) {
        val drop = GroundItem(ctx, item, plr.position, ChunkUpdatableView.localView(plr))
        if (world.items.register(drop)) {
            plr.inventory[index] = null
            plr.playSound(Sounds.DROP_ITEM)
            plr.overlays.closeWindows()
        } else {
            plr.sendMessage("You cannot drop this here.")
        }
    } else {
        plr.overlays.open(DestroyItemDialogue(index, item.id))
    }
    logger.log(itemDrop, "{}: {}(x{})", plr.username, itemDef.name, box(item.amount))
}