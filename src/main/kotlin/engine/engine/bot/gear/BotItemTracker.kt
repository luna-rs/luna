
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
    }

    /**
     * Whether item tracking is enabled for the associated player.
     */
    val active = plr is Bot

    override fun add(newItem: Item) {
        if (active) {
            val tracker = (plr as? Bot)?.itemTracker ?: return
            // A total across containers can pass Int.MAX_VALUE, and the multiset throws past it.
            tracker.add(newItem.id, minOf(newItem.amount, Int.MAX_VALUE - tracker.count(newItem.id)))
        }
    }

    override fun remove(oldItem: Item) {
        if (active) {
            (plr as? Bot)?.itemTracker?.remove(oldItem.id, oldItem.amount)
        }
    }
}
