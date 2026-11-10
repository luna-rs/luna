package api.bot.action.trading

import api.attr.Attr
import engine.bot.gear.BotItemTracker.Companion.itemOwnershipRevision
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Indexes a listener's transferable stock and preserves its working supplies and usable personal equipment.
 *
 * Ownership checks reject absent requested items before building either index. Container revisions invalidate
 * physical stock immediately; personal reserves also expire after five seconds and are refreshed for a selected
 * visit. Live supply targets are checked separately, including requirements recorded without an item movement.
 * Equipped and dynamic items never enter the transferable index.
 *
 * @author lare96
 */
internal object PlayerTradeSaleStock {

    /**
     * Per-listener indexes allocated only after a requested commodity is actually owned.
     *
     * @author lare96
     */
    private class Cache {
        var revision = Long.MIN_VALUE
        var inventory = emptyMap<Int, Int>()
        var bank = emptyMap<Int, Int>()
        var reserveRevision = Long.MIN_VALUE
        var reserveExpires = Long.MIN_VALUE
        var retained = emptyMap<Int, Int>()
    }

    private val cache = Attr.obj { Cache() }

    /**
     * Counts surplus across safe bank and inventory stock, including goods already on this bot's offer screen.
     */
    fun available(bot: Bot, id: Int, offered: Collection<Item> = emptyList()): Int {
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return 0
        if (id <= 0 || id == 995 || !definition.isTradeable || definition.unnotedId.isPresent) return 0
        val note = definition.notedId.orElse(-1)
        val onOffer = if (offered.isEmpty()) 0L else offered.asSequence()
            .filter { !it.isDynamic && (it.id == id || it.id == note) }.sumOf { it.amount.toLong() }
        val owned = bot.itemTracker.count(id).toLong() +
                (if (note >= 0) bot.itemTracker.count(note).toLong() else 0L) + onOffer
        if (owned <= 0) return 0
        val state = stock(bot)
        val transferable = count(state.inventory, id, note) + count(state.bank, id, note) + onOffer
        if (transferable <= 0) return 0
        val now = System.currentTimeMillis()
        val reserve = retained(bot, state, id, now)
        return minOf(transferable, (owned - reserve).coerceAtLeast(0), Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Keeps working copies already carried by the paused activity rather than replacing them with banked reserves.
     * Equipped copies satisfy the same retained quota first. The selected visit captures this count before travel,
     * so newly withdrawn sale stock is additional inventory rather than another protected working copy.
     */
    fun carriedReserve(bot: Bot, id: Int): Int {
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return 0
        if (id <= 0 || id == 995 || !definition.isTradeable || definition.unnotedId.isPresent) return 0
        val note = definition.notedId.orElse(-1)
        val state = stock(bot)
        val held = count(state.inventory, id, note)
        if (held <= 0) return 0
        val equipped = bot.equipment.computeAmountForId(id).toLong() +
                if (note >= 0) bot.equipment.computeAmountForId(note).toLong() else 0L
        val needed = (retained(bot, state, id, System.currentTimeMillis()).toLong() - equipped).coerceAtLeast(0)
        return minOf(held, needed, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Returns safe physical ids for one commodity without converting bank notes or including equipped copies.
     */
    fun physical(bot: Bot, id: Int, includeBank: Boolean = false): Map<Int, Int> {
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return emptyMap()
        if (id <= 0 || id == 995 || !definition.isTradeable) return emptyMap()
        val note = definition.notedId.orElse(-1)
        val state = stock(bot)
        val result = linkedMapOf<Int, Int>()
        for (physicalId in listOf(id, note)) {
            if (physicalId < 0) continue
            val amount = (state.inventory[physicalId] ?: 0).toLong() +
                    if (includeBank) (state.bank[physicalId] ?: 0).toLong() else 0L
            if (amount > 0) result[physicalId] = amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        return result
    }

    /**
     * Refreshes policy after selection or travel, before any goods are promised or withdrawn.
     */
    fun refresh(bot: Bot) {
        bot.attributes()[cache.attr].reserveExpires = Long.MIN_VALUE
    }

    private fun retained(bot: Bot, state: Cache, id: Int, now: Long): Int {
        if (state.reserveRevision != bot.itemOwnershipRevision || now >= state.reserveExpires) {
            state.retained = BotTradeIntentions.retainedStock(bot)
            state.reserveRevision = bot.itemOwnershipRevision
            state.reserveExpires = now + 5_000L
        }
        return maxOf(state.retained[id] ?: 0, BotTradeIntentions.saleSupplyTarget(bot, id, now))
    }

    private fun stock(bot: Bot): Cache {
        val state = bot.attributes()[cache.attr]
        if (state.revision != bot.itemOwnershipRevision) {
            state.inventory = index(bot.inventory.filterNotNull())
            state.bank = index(bot.bank.filterNotNull())
            state.revision = bot.itemOwnershipRevision
        }
        return state
    }

    private fun index(items: List<Item>): Map<Int, Int> {
        val amounts = HashMap<Int, Int>()
        val blocked = HashSet<Int>()
        for (item in items) {
            if (item.isDynamic) {
                blocked += item.id
            } else if (item.itemDef.isTradeable && item.amount > 0) {
                amounts[item.id] = ((amounts[item.id] ?: 0).toLong() + item.amount)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
        }
        blocked.forEach(amounts::remove)
        return amounts
    }

    private fun count(amounts: Map<Int, Int>, id: Int, note: Int): Long =
        (amounts[id] ?: 0).toLong() + if (note >= 0) (amounts[note] ?: 0).toLong() else 0L
}
