package game.content.castleWars

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.InventoryOverlayInterface
import io.luna.net.msg.out.WidgetItemsMessageWriter

/**
 * Lanthus's ticket exchange. Transactions validate price and capacity before changing inventory.
 *
 * @author lare96
 */
object CastleWarsRewards {
    val prices = linkedMapOf(4071 to 4, 4068 to 5, 4070 to 6, 4072 to 6, 4069 to 8,
        4506 to 40, 4503 to 50, 4505 to 60, 4507 to 60, 4504 to 80,
        4511 to 400, 4508 to 500, 4510 to 600, 4512 to 600, 4509 to 800,
        4513 to 10, 4514 to 10, 4515 to 10, 4516 to 10)

    fun open(player: Player) {
        if (!player.position.isWithinDistance(CastleWarsGame.LOBBY, 16)) return
        CastleWars.game.claimTickets(player)
        player.overlays.open(CastleWarsRewardInterface())
        refresh(player)
    }

    private fun refresh(player: Player) {
        player.queue(WidgetItemsMessageWriter(12392, prices.keys.map { Item(it, 100) }))
        player.queue(WidgetItemsMessageWriter(12393, emptyList<Item>()))
        player.queue(WidgetItemsMessageWriter(13294, player.inventory.toList()))
    }

    fun exchange(player: Player, id: Int, buy: Boolean): Boolean {
        val cost = prices[id] ?: return false
        if (CastleWarsRewardInterface::class.java.let { player.overlays.getOverlay(it) } == null ||
            !player.position.isWithinDistance(CastleWarsGame.LOBBY, 16)) return false
        val inventory = player.inventory
        if (buy) {
            val tickets = inventory.computeAmountForId(4067)
            if (tickets < cost || inventory.isFull && tickets != cost) return false
            check(inventory.remove(Item(4067, cost)))
            check(inventory.add(Item(id)))
        } else {
            if (!inventory.contains(id) || !inventory.hasSpaceFor(Item(4067, cost))) return false
            check(inventory.remove(id))
            check(inventory.add(Item(4067, cost)))
        }
        refresh(player)
        return true
    }
}

/**
 * Identifies the ticket exchange so forged widget clicks cannot buy or sell elsewhere.
 *
 * @author lare96
 */
class CastleWarsRewardInterface : InventoryOverlayInterface(11367, 13293)

