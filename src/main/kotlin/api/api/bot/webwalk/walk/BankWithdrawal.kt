package api.bot.webwalk.walk

import api.bot.action.BotActionHandler
import api.bot.webwalk.model.BankDefinition
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.plan.BankItems
import api.bot.webwalk.plan.CapabilitySnapshot
import api.bot.webwalk.plan.Withdrawal
import api.predef.*
import engine.bank.Banking
import game.item.degradable.jewellery.TeleportJewellery
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject

/**
 * What a bot has in its inventory and bank, copied on the game thread so that [BankWithdrawal.choose] can run on any thread.
 *
 * @property inventory The amount of each item in the inventory, keyed by item id.
 * @property freeSlots The free slots of the inventory.
 * @property bank The amount of each item in the bank that a trip can spend (see [BankItems]), keyed by item id.
 * @property stackable The ids of the items in the bank that stack in the inventory.
 * @property wornJewellery The names of the teleport jewellery that the bot wears a charged piece of.
 *
 * @author Hydrozoa
 */
data class BankView(val inventory: Map<Int, Int> = emptyMap(),
                    val freeSlots: Int = 0,
                    val bank: Map<Int, Int> = emptyMap(),
                    val stackable: Set<Int> = emptySet(),
                    val wornJewellery: Set<String> = emptySet())

/**
 * Works out what a bot withdraws from its bank for a trip, and withdraws it. This is the only place that decides whether a
 * withdrawal fits in the inventory: [highestFitting] before anything is planned, and [choose] for a finished plan.
 *
 * A withdrawal never deposits anything to make room. What doesn't fit isn't withdrawn, and a trip that can't be taken
 * without it isn't taken.
 *
 * @author Hydrozoa
 */
object BankWithdrawal {

    /**
     * Copies what [choose] needs of a bot. Must be called on the game thread, because it reads the bot as it is.
     *
     * Only the items of the bank that a trip can spend are in the copy, which is all that a withdrawal for a trip can
     * take.
     *
     * @param bot The bot.
     * @param banked The items of the bank that a trip can spend, which a trip copies once for all that it plans. By default
     * they are copied from the bank.
     * @return The copy.
     */
    fun view(bot: Bot, banked: BankItems = BankItems.of(bot)): BankView {
        val bank = banked.amounts
        val worn = TeleportJewellery.entries.filter { jewellery -> jewellery.items.any { bot.equipment.contains(it) } }
            .map { it.name }.toSet()
        return BankView(count(bot.inventory), bot.inventory.computeRemainingSize(), bank,
                        bank.keys.filter { bot.inventory.stackable(it) }.toSet(), worn)
    }

    /**
     * Finds the most of the bank that a trip could use at all, before anything is planned. With a free slot that is
     * [bank]. With none, coins can still be added to a stack of coins that is already in the inventory, so it is
     * [BankAccess.COINS] if that is allowed, and [BankAccess.NONE] otherwise. (A full inventory that already holds a stack of
     * runes is rare enough that [BankAccess.ALL] isn't worked out for it.)
     *
     * @param view What the bot has.
     * @param bank The most of the bank that the trip may use.
     * @return The most of it that could fit.
     */
    fun highestFitting(view: BankView, bank: BankAccess): BankAccess = when {
        bank == BankAccess.NONE || view.freeSlots > 0 -> bank
        (view.inventory[CapabilitySnapshot.COINS] ?: 0) > 0 && (view.bank[CapabilitySnapshot.COINS] ?: 0) > 0 -> BankAccess.COINS
        else -> BankAccess.NONE
    }

    /**
     * Chooses the items to withdraw for a trip. All of what is required is withdrawn, or nothing is: if the bank has too
     * little of it, or it doesn't fit, there is no withdrawal. What is wanted on top of that is withdrawn if the bank has it
     * and it fits.
     *
     * Items are withdrawn as they are asked for. Charges of jewellery are withdrawn as pieces, with the most charges first,
     * one slot each. A stackable item takes a slot unless the inventory already has a stack of it, and anything else takes
     * a slot for each one.
     *
     * @param view What the bot has.
     * @param withdrawal What the trip requires and wants from the bank.
     * @param keepFree The slots to leave free, such as one for worn jewellery that is taken off to be rubbed (see
     * [WebWalkExecutor]), because the executor deposits the whole inventory to make room for it if there is none.
     * @return The items to withdraw, or `null` if what is required can't be withdrawn.
     */
    fun choose(view: BankView, withdrawal: Withdrawal, keepFree: Int): List<Item>? {
        val bankLeft = HashMap(view.bank)
        val chosen = LinkedHashMap<Int, Int>()
        var slots = view.freeSlots - keepFree

        // Takes some of an item out of what is left in the bank, if it fits.
        fun take(id: Int, amount: Int): Boolean {
            val stacks = id in view.stackable
            val needed = if (!stacks) amount else if ((view.inventory[id] ?: 0) > 0 || id in chosen) 0 else 1
            if (amount > (bankLeft[id] ?: 0) || needed > slots) {
                return false
            }
            slots -= needed
            bankLeft[id] = bankLeft.getValue(id) - amount
            chosen.merge(id, amount, Int::plus)
            return true
        }

        // Takes pieces of jewellery, the most charges first, until they have at least a number of charges.
        fun takeCharges(name: String, charges: Int, required: Boolean): Boolean {
            val jewellery = TeleportJewellery.named(name) ?: return !required
            var left = charges
            for (id in jewellery.items) {
                while (left > 0 && (bankLeft[id] ?: 0) > 0) {
                    if (!take(id, 1)) {
                        return !required
                    }
                    left -= jewellery.charges(id)
                }
            }
            return left <= 0 || !required
        }

        val required = withdrawal.required
        for ((id, amount) in required.items) {
            if (!take(id, amount)) {
                return null
            }
        }
        for ((name, charges) in required.charges) {
            if (!takeCharges(name, charges, true)) {
                return null
            }
        }

        // The buffer, which is dropped where it doesn't fit.
        val wanted = withdrawal.wanted
        for ((id, amount) in wanted.items) {
            val extra = minOf(amount - (required.items[id] ?: 0), bankLeft[id] ?: 0)
            if (extra > 0) {
                take(id, extra)
            }
        }
        for ((name, charges) in wanted.charges) {
            val jewellery = TeleportJewellery.named(name) ?: continue
            val taken = jewellery.items.sumOf { (chosen[it] ?: 0) * jewellery.charges(it) }
            if (charges > taken) {
                takeCharges(name, charges - taken, false)
            }
        }
        return chosen.map { (id, amount) -> Item(id, amount) }
    }

    /**
     * Withdraws items from the open bank of a bot, never as notes, and checks that each arrived in the inventory, since the
     * banking only checks that it left the bank.
     *
     * @param bot The bot.
     * @param handler Its action handler.
     * @param items The items, as [choose] chose them.
     * @return The items that arrived in the inventory, which are all of them unless a withdrawal failed. It stops at the
     * first that fails.
     */
    suspend fun withdraw(bot: Bot, handler: BotActionHandler, items: List<Item>): List<Item> {
        if (!bot.bank.isOpen) {
            return emptyList()
        }
        handler.banking.clickBankingMode(false)
        val withdrawn = ArrayList<Item>(items.size)
        for (item in items) {
            val before = bot.inventory.computeAmountForId(item.id)
            handler.banking.withdraw(item)
            val arrived = bot.inventory.computeAmountForId(item.id) - before
            if (arrived > 0) {
                withdrawn += Item(item.id, arrived)
            }
            if (arrived < item.amount) {
                bot.log("WebWalk: withdrew $arrived of ${item.amount} x ${itemName(item.id)}.")
                break
            }
        }
        return withdrawn
    }

    /**
     * Opens a bank of the web. Must be called from a bot script, on the game thread, with the bot at the bank.
     *
     * @param bot The bot.
     * @param handler Its action handler.
     * @param bank The bank.
     * @return `true` if the bank is open.
     */
    suspend fun openBank(bot: Bot, handler: BotActionHandler, bank: BankDefinition): Boolean {
        val booth = booth(bank)
        if (booth == null) {
            bot.log("WebWalk: there is no booth at ${bank.booth}.")
            return false
        }
        return handler.banking.open(booth) && bot.bank.isOpen
    }

    /**
     * Finds the booth of a bank of the web.
     *
     * @param bank The bank.
     * @return The booth, or `null` if there is no bank object on its tile.
     */
    fun booth(bank: BankDefinition): GameObject? =
        world.locator.findObjectsOnTile(bank.booth) { it.id in Banking.bankingObjects }.firstOrNull()

    /**
     * Counts the amount of each item in a container.
     */
    private fun count(container: ItemContainer): Map<Int, Int> {
        val totals = HashMap<Int, Int>()
        for (item in container) {
            if (item != null) {
                totals.merge(item.id, item.amount) { a, b -> (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }
            }
        }
        return totals
    }
}
