
package engine.bot.gear

import api.attr.Attr
import com.google.common.collect.HashMultiset
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemTracker
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * Tracks item ownership across a bot's observed item containers.
 *
 * Updates the bot's [Bot.itemTracker] as items are added to or removed from containers such as the inventory,
 * equipment, and bank. This provides a centralized record of owned item quantities without repeatedly scanning
 * individual containers.
 *
 * Tracking is exclusive to [Bot] instances. Item changes belonging to regular players are ignored.
 *
 * @param plr The player whose item containers are being observed.
 * @author lare96
 */
class BotItemTracker(val plr: Player) : ItemTracker() {

    companion object {

        /**
         * The total quantity of each item owned by this bot.
         *
         * Stored as a multiset mapping item ids to their accumulated quantities across all tracked containers.
         * Each bot maintains its own instance through the attribute system.
         */
        val Bot.itemTracker by Attr.obj { HashMultiset.create<Int>() }

        /**
         * Invalidates derived ownership indexes only when observed container quantities change.
         */
        var Bot.itemOwnershipRevision by Attr.long()
            private set
    }

    /**
     * Whether item tracking is enabled for the associated player.
     */
    val active = plr is Bot

    override fun add(newItem: Item) {
        if (active) {
            val bot = plr as? Bot ?: return
            val tracker = bot.itemTracker
            // A total across containers can pass Int.MAX_VALUE, and the multiset throws past it.
            val added = minOf(newItem.amount, Int.MAX_VALUE - tracker.count(newItem.id))
            if (added > 0) {
                tracker.add(newItem.id, added)
                bot.itemOwnershipRevision++
            }
        }
    }

    override fun remove(oldItem: Item) {
        if (active) {
            val bot = plr as? Bot ?: return
            val tracker = bot.itemTracker
            if (oldItem.amount > 0 && tracker.count(oldItem.id) > 0) {
                tracker.remove(oldItem.id, oldItem.amount)
                bot.itemOwnershipRevision++
            }
        }
    }
}
