package world.player.dropDeathItems

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.DeathEvent
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Player.SkullIcon
import java.util.*


// TODO system for NPC drops and handling NPC deaths
/**
 * The comparator that will be used to compare prices.
 */
val priceComparator: Comparator<Item> = Comparator.comparingInt<Item> { it.unnotedItemDef.value }.reversed()

/**
 * Items will be lost if the player is below this rank.
 */
val loseItemsBelow = RIGHTS_ADMIN

/**
 * Drop player items. Keep top 3, influenced by skulled status and prayer.
 */
fun dropItems(plr: Player, source: Mob?) {
    val deathItems = LinkedList<Item>()
    var keepAmount = 3
    if (plr.skullIcon == SkullIcon.WHITE) {
        keepAmount = 0
    }
    // TODO Keep extra item on death prayer.

    // Remove all tradeable inventory and equipment items.
    removeItems(plr.inventory, deathItems)
    removeItems(plr.equipment, deathItems)

    if (deathItems.size > 0) {
        deathItems.sortWith(priceComparator)
        val stackableDeathItems = ArrayList<Item>()
        while (keepAmount > 0) {
            val keepItem = deathItems.poll()
            if (keepItem == null || !plr.inventory.hasSpaceFor(keepItem)) {
                break
            }
            val keepItemAmount = keepItem.amount
            if (keepItemAmount > 1) { // If stackable, drop all but one.
                stackableDeathItems.add(keepItem.withAmount(keepItemAmount - 1))
                plr.inventory.add(keepItem.withAmount(1))
            } else {
                plr.inventory.add(keepItem)
            }
            keepAmount--
        }
        deathItems.addAll(stackableDeathItems)

        val sourcePlayer = if (source is Player) source else null
        if (sourcePlayer != null) {
            world.addItem(526, 1, plr.position, sourcePlayer)
            while (true) {
                val item = deathItems.poll() ?: break
                world.addItem(item.id, item.amount, plr.position, sourcePlayer)
            }
        }
    }
}

/**
 * Removes items from the container and adds it to the list.
 */
fun removeItems(items: ItemContainer, deathItems: LinkedList<Item>) {
    items.forIndexedItems { index, item ->
        if (item != null && item.itemDef.isTradeable) {
            deathItems.add(item)
            items.set(index, null)
        }
    }
}

/**
 * Listen for death.
 */
on(DeathEvent::class).filter { mob.type == TYPE_PLAYER }.then {
    val plr = mob.asPlr()
    if (plr.rights < loseItemsBelow) {
        dropItems(plr, source)
    }
}