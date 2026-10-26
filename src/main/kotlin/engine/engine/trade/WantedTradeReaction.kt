package engine.trade

import api.predef.ext.wantedItemsToAcquire
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.speech.BotReactions
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot

/**
 * Records wanted stock before an exchange and checks for a real ownership gain after both sides receive their items.
 *
 * Offered items are no longer in the inventory tracker, so they are included in the original ownership count.
 * Notes and unnoted items share a count. This prevents swapping or returning existing stock from creating a reward.
 *
 * @property bot The bot participating in the trade.
 * @property beforeOwned Original holdings of eligible wanted items that were below their ownership targets.
 * @author lare96
 */
internal class WantedTradeReaction private constructor(
    private val bot: Bot,
    private val beforeOwned: Map<Int, Long>
) {
    companion object {
        /**
         * Captures unmet acquisition goals immediately before the native trade transfers its offers.
         *
         * @param player The participant, which may be a human player.
         * @param offered Items already removed from this participant's inventory into its trade offer.
         * @return A pending reaction for a bot with unmet wants, otherwise `null`.
         */
        fun capture(player: Player, offered: Iterable<Item?>): WantedTradeReaction? {
            val bot = player as? Bot ?: return null
            val escrow = offered.filterNotNull().groupBy { it.itemDef.unnotedId.orElse(it.id) }
                .mapValues { (_, items) -> items.sumOf { it.amount.toLong() } }
            val before = bot.preferences.wantedItemsToAcquire().mapNotNull { wanted ->
                val owned = owned(bot, wanted.id) + (escrow[wanted.id] ?: 0L)
                if (owned < wanted.target) wanted.id to owned else null
            }.toMap()
            return if (before.isEmpty()) null else WantedTradeReaction(bot, before)
        }

        /**
         * Counts tracked inventory, bank and equipment stock without overflowing when noted stock is added.
         *
         * @param bot The stock owner.
         * @param id The unnoted item id.
         * @return Total ownership across the item's noted and unnoted forms.
         */
        private fun owned(bot: Bot, id: Int): Long {
            val noted = ItemDefinition.ALL.retrieve(id).notedId
            val notedAmount = if (noted.isPresent && noted.asInt != id) bot.itemTracker.count(noted.asInt) else 0
            return bot.itemTracker.count(id).toLong() + notedAmount
        }
    }

    /** Whether this completed exchange has already been checked. */
    private var checked = false

    /**
     * Rewards progress toward at least one wanted-item goal, at most once for this exchange.
     * This must only be called after both native inventory transfers finish without an exception.
     */
    fun complete() {
        if (checked) return
        checked = true
        if (beforeOwned.any { (id, before) -> owned(bot, id) > before }) {
            BotReactions.reactToWantedTrade(bot)
        }
    }
}
