package api.bot.webwalk.plan

import game.item.degradable.jewellery.TeleportJewellery
import game.skill.magic.CombinationRune
import game.skill.magic.ItemRequirement
import game.skill.magic.Rune
import game.skill.magic.teleportSpells.TeleportSpell
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * The items in the bank of a bot that a trip can spend, copied from the bank once so that a trip is planned without
 * reading the whole bank again for each thing that it counts (see [BotCapabilities.snapshot], [TeleportAvailability] and
 * [api.bot.webwalk.walk.BankWithdrawal.view]).
 *
 * Only the following items are kept: coins, runes and combination runes, teleport jewellery, and the other items that
 * teleport spells take. They are kept in the order of the bank, so that the runes a spell would take from them are the
 * same as from the whole bank.
 *
 * @property items The items, in the order of the bank.
 *
 * @author Hydrozoa
 */
class BankItems(val items: List<Item>) {

    companion object {

        /**
         * The ids of the items that a trip can spend.
         */
        val IDS: Set<Int> = buildSet {
            add(CapabilitySnapshot.COINS)
            Rune.entries.forEach { add(it.id) }
            CombinationRune.entries.forEach { add(it.id) }
            TeleportJewellery.entries.forEach { addAll(it.items) }
            TeleportSpell.entries.forEach { spell -> spell.requirements.filterIsInstance<ItemRequirement>().forEach { add(it.id) } }
        }

        /**
         * No items, for a trip that doesn't count the bank.
         */
        val EMPTY = BankItems(emptyList())

        /**
         * Copies the items of [IDS] from the bank of a bot. Must be called on the game thread, because it reads the bank as
         * it is.
         *
         * @param bot The bot.
         * @return The copy.
         */
        fun of(bot: Bot): BankItems = BankItems(bot.bank.mapNotNull { item -> item?.takeIf { it.id in IDS } })
    }

    /**
     * The amount of each item, keyed by item id.
     */
    val amounts: Map<Int, Int> = HashMap<Int, Int>().also { totals ->
        for (item in items) {
            totals.merge(item.id, item.amount) { a, b -> (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        }
    }

    /**
     * @return The amount of an item, or `0` if there is none.
     */
    fun amount(id: Int): Int = amounts[id] ?: 0
}
