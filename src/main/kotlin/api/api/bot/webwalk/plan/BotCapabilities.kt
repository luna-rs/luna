package api.bot.webwalk.plan

import api.bot.webwalk.model.TeleportDefinition
import engine.controllers.WildernessLocatableController.wildernessLevel
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot

/**
 * Copies what a bot can do into a [CapabilitySnapshot].
 *
 * @author Hydrozoa
 */
object BotCapabilities {

    /**
     * Takes a snapshot of a bot. Must be called on the game thread, because it reads the bot as it is.
     *
     * The items are those in the inventory and equipment of the bot. Its bank is left out with [BankAccess.NONE], because a
     * trip can't use what a bot has to go and get first. With more [bank] access, what the bank has of the things that a
     * trip spends counts as if it were carried, so that the web-walker can plan a trip that withdraws it first:
     *
     * - [BankAccess.COINS]: the coins in the bank.
     * - [BankAccess.ALL]: the coins, the runes (and combination runes) that teleport spells would take from the bank after
     *   the inventory, and the charges of the teleport jewellery in the bank.
     *
     * Keys, staves and other items in the bank never count, so the items that an edge asks for are still met by what is
     * carried. Whether a withdrawal would fit in the inventory isn't looked at here, see
     * [api.bot.webwalk.walk.BankWithdrawal].
     *
     * The flags are the unlocks that are given, and the [BotConditions] that the bot meets. The charges of its teleport
     * jewellery, and what each teleport that it can use would use up, are counted too, see [TeleportAvailability].
     *
     * @param bot The bot.
     * @param teleports The teleports that the web has, to find which of them the bot can use right now.
     * @param flags The unlocks of the bot.
     * @param bank How much of the bank counts.
     * @param banked The items of the bank that a trip can spend. A trip that takes several snapshots copies them once and
     * gives the same copy to each. By default they are copied from the bank, unless [bank] is [BankAccess.NONE].
     * @return The snapshot.
     */
    fun snapshot(bot: Bot,
                 teleports: List<TeleportDefinition> = emptyList(),
                 flags: Set<String> = emptySet(),
                 bank: BankAccess = BankAccess.NONE,
                 banked: BankItems = if (bank == BankAccess.NONE) BankItems.EMPTY else BankItems.of(bot)): CapabilitySnapshot {
        val skills = Skill.IDS.associateWith { bot.skills.getSkill(it).staticLevel }
        val items = HashMap<Int, Int>()
        count(bot.inventory, items)
        count(bot.equipment, items)
        val fromBank = if (bank == BankAccess.ALL) banked else null
        val usable = TeleportAvailability.usable(bot, teleports, fromBank)
        val spends = teleports.filter { it.id in usable }.associate { it.id to TeleportAvailability.spend(bot, it, fromBank) }
            .filterValues { !it.isEmpty }
        if (bank != BankAccess.NONE) {
            val ids = HashSet<Int>()
            ids += CapabilitySnapshot.COINS
            if (fromBank != null) {
                spends.values.forEach { ids += it.items.keys }
            }
            for (id in ids) {
                add(items, id, banked.amount(id))
            }
        }
        return CapabilitySnapshot(skills, items, flags + BotConditions.met(bot), bot.wildernessLevel, usable,
                                  TeleportAvailability.charges(bot, fromBank), spends)
    }

    /**
     * Adds the amount of every item of a container to a total.
     */
    private fun count(container: ItemContainer, totals: MutableMap<Int, Int>) {
        for (item in container) {
            if (item != null) {
                add(totals, item.id, item.amount)
            }
        }
    }

    /**
     * Adds an amount of an item to a total, which stops at [Int.MAX_VALUE] instead of overflowing, as a bank and an
     * inventory of coins together may.
     */
    private fun add(totals: MutableMap<Int, Int>, id: Int, amount: Int) {
        if (amount > 0) {
            totals.merge(id, amount) { a, b -> (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
        }
    }
}
