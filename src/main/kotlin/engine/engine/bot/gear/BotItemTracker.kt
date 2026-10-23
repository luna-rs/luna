package engine.bot.gear

import api.attr.Attr
import com.google.common.collect.HashMultiset
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.item.ItemContainerListener
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * Tracks the total amount of each item currently owned by a bot.
 *
 * This listener is intended to be attached to item containers that contribute to a bot's total local ownership, such as
 * inventory, equipment, and bank containers. As containers initialize or change, the listener updates the bot's
 * [Bot.itemTracker] multiset so bot systems can quickly query whether an item is owned without repeatedly scanning every
 * container.
 *
 * Non-bot players are ignored. This allows the same listener type to be installed through shared player/container setup
 * code without tracking item counts for normal players.
 *
 * @param plr The player whose container changes are being observed.
 * @author lare96
 */
class BotItemTracker(val plr: Player) : ItemContainerListener {

    companion object {

        /**
         * A per-bot multiset of locally owned item ids.
         *
         * Each item id is stored with a count equal to the total amount currently observed across the containers this
         * listener is attached to. Stackable items contribute their full amount, while non-stackable items usually
         * contribute one per slot.
         */
        val Bot.itemTracker by Attr.obj { HashMultiset.create<Int>() }
    }

    /**
     * Whether this listener should update item tracking state.
     *
     * Only [Bot] instances are tracked. Normal players still receive listener callbacks, but those callbacks are ignored.
     */
    val active = plr is Bot

    /**
     * Adds all existing container items to the bot item tracker.
     *
     * This is called when a container is first initialized or when this listener is first attached. Every non-null item in
     * the container contributes its id and amount to [Bot.itemTracker].
     *
     * @param items The initialized item container.
     */
    override fun onInit(items: ItemContainer) {
        if (active) {
            val bot = plr as Bot
            items.forEach {
                if (it != null) {
                    bot.itemTracker.add(it.id, it.amount)
                }
            }
        }
    }

    /**
     * Updates the bot item tracker after a single-slot container change.
     *
     * The previous item is removed from the tracker and the new item is added. This keeps the tracked counts aligned with
     * the container after replaces, inserts, removals, and amount changes.
     *
     * @param index The slot index that changed.
     * @param items The container that changed, or `null` if unavailable.
     * @param oldItem The item previously in the slot, or `null` if the slot was empty.
     * @param newItem The item currently in the slot, or `null` if the slot is now empty.
     */
    override fun onSingleUpdate(index: Int, items: ItemContainer?, oldItem: Item?, newItem: Item?) {
        update(oldItem, newItem)
    }

    /**
     * Updates the bot item tracker after a bulk container change.
     *
     * Each changed slot should remove its previous item contribution and add its new item contribution. This method
     * delegates to [update] so single and bulk updates apply identical count logic.
     *
     * @param index The changed slot index.
     * @param items The container that changed, or `null` if unavailable.
     * @param oldItem The item previously in the slot, or `null` if the slot was empty.
     * @param newItem The item currently in the slot, or `null` if the slot is now empty.
     */
    override fun onBulkUpdate(index: Int, items: ItemContainer?, oldItem: Item?, newItem: Item?) {
        update(oldItem, newItem)
    }

    /**
     * Applies one item replacement to the bot item tracker.
     *
     * The old item amount is removed first, then the new item amount is added. This handles item replacement and amount
     * changes safely as long as container events provide the previous and current slot contents.
     *
     * @param oldItem The item being removed from the observed container slot, or `null` if none existed.
     * @param newItem The item being added to the observed container slot, or `null` if the slot is now empty.
     */
    private fun update(oldItem: Item?, newItem: Item?) {
        if (active) {
            val bot = plr as Bot
            if (oldItem != null) {
                bot.itemTracker.remove(oldItem.id, oldItem.amount)
            }
            if (newItem != null) {
                bot.itemTracker.add(newItem.id, newItem.amount)
            }
        }
    }
}