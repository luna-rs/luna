package world.player.death.dropItems

import api.predef.*
import io.luna.game.event.impl.PlayerDeathEvent
import io.luna.game.model.EntityType
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Player.SkullIcon
import java.util.*

/**
 * The comparator that will be used to compare prices.
 */
val priceComparator: Comparator<Item> = Comparator.comparingInt<Item> { it.itemDef.value }.reversed()

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
        while (keepAmount > 0) {
            val keepItem = deathItems.poll()
            if (keepItem == null || !plr.inventory.hasSpaceFor(keepItem)) {
                break
            }
            plr.inventory.add(keepItem)
            keepAmount--
        }

        val sourcePlayer = if (source?.type != EntityType.PLAYER) null else source.asPlr()
        world.addItem(526, 1, plr.position, sourcePlayer)
        while (true) {
            val item = deathItems.poll() ?: break
            world.addItem(item.id, item.amount, plr.position, sourcePlayer)
        }
    }
}

/**
 * Removes items from the container and adds it to the list.
 */
fun removeItems(items: ItemContainer, deathItems: LinkedList<Item>) {
    items.forItems { index, item ->
        if (item != null && item.itemDef.isTradeable) {
            deathItems.add(item)
            items.set(index, null)
        }
    }
}

/**
 * Listen for death.
 */
on(PlayerDeathEvent::class) {
    if (plr.rights < loseItemsBelow) {
        dropItems(plr, source)
    }
}