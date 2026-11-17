package api.bot.action.trading

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotSupplySources
import api.predef.ext.canUseWantedItem
import api.predef.ext.totalItemAmount
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.trade.tradingWith
import game.skill.mining.Pickaxe
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.Skill

/**
 * Keeps one concrete generated purchase beside its longer term equipment aspiration and working fallback.
 * Failed source probes never become independent literal supplies. Ownership, promises and payment are rechecked
 * by trade admission; explicit activity inputs, production materials and manual wishes retain exact semantics.
 *
 * @author lare96
 */
object BotGearDemand {
    /**
     * Ordered meaningful replacements, strongest first, for one activity family or combat role and slot.
     *
     * @author lare96
     */
    data class Need(val key: String, val aspiration: Int, val selected: Int, val alternatives: List<Int>,
                    val fallback: Int?, val expires: Long, val maximumCoins: Int)

    private val needs = Attr.map<String, Need>()
    private var Bot.savedNeeds by Attr.string().persist("bot_generated_gear_needs")
    private var Bot.acquisitionState by Attr.string().persist("bot_tool_acquisition")

    private fun state(bot: Bot): HashMap<String, Need> {
        val attributes = bot.attributes()
        if (attributes.has(needs.attr)) return attributes[needs.attr]
        val values = attributes[needs.attr]
        for (entry in bot.savedNeeds.split(';').take(48)) {
            val parts = entry.split('=')
            if (parts.size != 2 || parts[0].length > 64 ||
                !parts[0].startsWith("tool:") && !parts[0].startsWith("gear:")) continue
            val fields = parts[1].split(',')
            if (fields.size != 6) continue
            val aspiration = fields[0].toIntOrNull()?.takeIf { it in 0..65535 } ?: continue
            val selected = fields[1].toIntOrNull()?.takeIf { it in -1..65535 } ?: continue
            val fallback = fields[2].toIntOrNull()?.takeIf { it in -1..65535 } ?: continue
            val expires = fields[3].toLongOrNull()?.takeIf { it > 0L } ?: continue
            val coins = fields[4].toIntOrNull()?.takeIf { it >= 0 } ?: continue
            val alternatives = fields[5].split('.').take(128).mapNotNull { it.toIntOrNull()?.takeIf { id -> id in 0..65535 } }
                .distinct()
            if (alternatives.isEmpty() || selected >= 0 && selected !in alternatives) continue
            values[parts[0]] = Need(parts[0], aspiration, selected, alternatives, fallback.takeIf { it >= 0 }, expires, coins)
        }
        return values
    }

    private fun saveNeeds(bot: Bot) {
        bot.savedNeeds = state(bot).values.joinToString(";") {
            "${it.key}=${it.aspiration},${it.selected},${it.fallback ?: -1},${it.expires},${it.maximumCoins},${it.alternatives.joinToString(".")}"
        }
    }

    /**
     * Shares an absolute deadline and failed-attempt backoff across coroutine pauses, new sessions and saves.
     * The compact persisted value contains only skill ids and wall-clock deadlines, never inventory state.
     *
     * @author lare96
     */
    private data class Acquisition(val deadline: Long, val retryAfter: Long)

    private fun acquisitions(bot: Bot): MutableMap<Int, Acquisition> = bot.acquisitionState.split(';')
        .mapNotNull { entry ->
            val fields = entry.split(':')
            if (fields.size != 3) return@mapNotNull null
            val skill = fields[0].toIntOrNull()?.takeIf { it in 0..22 } ?: return@mapNotNull null
            val deadline = fields[1].toLongOrNull()?.takeIf { it >= 0L } ?: return@mapNotNull null
            val retry = fields[2].toLongOrNull()?.takeIf { it >= 0L } ?: return@mapNotNull null
            skill to Acquisition(deadline, retry)
        }.toMap().toMutableMap()

    private fun save(bot: Bot, values: Map<Int, Acquisition>) {
        bot.acquisitionState = values.toSortedMap().entries.joinToString(";") {
            "${it.key}:${it.value.deadline}:${it.value.retryAfter}"
        }
    }

    fun acquisitionDeadline(bot: Bot, skill: Int, now: Long = System.currentTimeMillis()): Long? {
        val values = acquisitions(bot)
        val previous = values[skill]
        if (previous != null && previous.retryAfter > now) return null
        if (previous != null && previous.deadline > 0L) {
            if (previous.deadline > now) return previous.deadline
            values[skill] = Acquisition(0L, now + 60_000L)
            save(bot, values)
            return null
        }
        val deadline = now + 120_000L
        values[skill] = Acquisition(deadline, 0L)
        save(bot, values)
        return deadline
    }

    fun acquisitionFinished(bot: Bot, skill: Int, success: Boolean, now: Long = System.currentTimeMillis()) {
        val values = acquisitions(bot)
        if (success) values.remove(skill) else values[skill] = Acquisition(0L, now + 60_000L)
        save(bot, values)
    }

    /**
     * Native gathering tools use current gathering levels independently of weapon wielding requirements.
     */
    fun usableNow(bot: Bot, id: Int): Boolean {
        if (!bot.canUseWantedItem(id)) return false
        Axe.entries.firstOrNull { it.id == id }?.let { return bot.skill(Skill.WOODCUTTING).level >= it.level }
        Pickaxe.ID_TO_PICKAXE[id]?.let { return bot.skill(Skill.MINING).level >= it.level }
        return bot.canUseWantedItem(id)
    }

    fun allowance(bot: Bot): Int = (BotTradeManager.transferableAmount(bot, 995).toDouble() *
        BotTradeIntentions.budgetFraction(bot, BotTradePurpose.BUY)).toLong().coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

    /**
     * Reads real finite stock without reserving it. The purchase owner must still enforce its captured price.
     */
    fun sourcePrice(bot: Bot, id: Int, maximumCoins: Int = allowance(bot)): Long? {
        val shop = BotSupplySources.quote(bot, id, 1)?.totalCoins?.takeIf { it in 1..maximumCoins.toLong() }
        return shop
    }

    /**
     * Chooses an attainable improvement without deleting the ideal tier when only an intermediate is available.
     */
    fun record(bot: Bot, key: String, aspiration: Int, ordered: Collection<Int>, fallback: Int? = null,
               expires: Long = System.currentTimeMillis() + 2 * 60 * 60_000L): Need? {
        val choices = ordered.distinct().filter { id -> usableNow(bot, id) &&
            ItemDefinition.ALL.get(id).map { it.isTradeable }.orElse(false) }
        if (choices.isEmpty()) { clear(bot, key); return null }
        val previous = state(bot)[key]
        if (bot.tradingWith != -1 && previous != null && previous.aspiration == aspiration) return previous
        val coins = allowance(bot)
        val selected = choices.firstOrNull { sourcePrice(bot, it, coins) != null } ?: aspiration.takeIf {
            it in choices && bot.world.economy.getPrice(it).toLong() in 1..coins.toLong()
        } ?: -1
        return Need(key, aspiration, selected, choices, fallback, expires, coins).also {
            state(bot)[key] = it
            saveNeeds(bot)
        }
    }

    fun current(bot: Bot, now: Long = System.currentTimeMillis()): List<Need> {
        val current = state(bot)
        if (current.entries.removeIf { it.value.expires <= now }) saveNeeds(bot)
        return current.values.toList()
    }

    fun clear(bot: Bot, key: String) { if (state(bot).remove(key) != null) saveNeeds(bot) }

    fun forItem(bot: Bot, id: Int): Need? {
        val current = current(bot)
        return current.firstOrNull { it.selected == id } ?:
            current.firstOrNull { it.key.startsWith("tool:") && id in it.alternatives } ?:
            current.firstOrNull { id in it.alternatives }
    }

    fun targets(bot: Bot): Set<Int> = current(bot).filter { it.selected >= 0 && deficit(bot, it.selected) > 0 }
        .mapTo(linkedSetOf()) { it.selected }

    /**
     * One physical or promised useful replacement satisfies this generated purchase, without erasing aspiration.
     */
    fun deficit(bot: Bot, id: Int, includePending: Boolean = true): Int {
        if (BotTradeIntentions.hasLiteralSupplyDemand(bot, id)) return Int.MAX_VALUE
        val need = forItem(bot, id) ?: return Int.MAX_VALUE
        if (need.alternatives.any { bot.totalItemAmount(it) > 0 }) return 0

        return 1
    }

    fun hasUsableOwned(bot: Bot, id: Int): Boolean = forItem(bot, id)?.alternatives.orEmpty().any {
        usableNow(bot, it) && bot.totalItemAmount(it) > 0
    }

    fun priceCeiling(bot: Bot, id: Int): Long {
        if (BotTradeIntentions.hasLiteralSupplyDemand(bot, id)) return Long.MAX_VALUE
        val need = forItem(bot, id) ?: return Long.MAX_VALUE
        return minOf(need.maximumCoins.toLong(), sourcePrice(bot, id, need.maximumCoins) ?: Long.MAX_VALUE)
    }

    fun constrain(quote: BotTradeItem): BotTradeItem? {
        val need = forItem(quote.bot, quote.item.id) ?: return quote
        if (BotTradeIntentions.hasLiteralSupplyDemand(quote.bot, quote.item.id)) return quote
        val count = minOf(quote.item.amount, deficit(quote.bot, quote.item.id))
        if (!usableNow(quote.bot, quote.item.id)) return null
        val maximum = need.maximumCoins.toLong()
        val source = sourcePrice(quote.bot, quote.item.id, maximum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val price = minOf(quote.price, source ?: Long.MAX_VALUE)
        return if (count < 1 || price < 1L || price > maximum) null else
            BotTradeItem(Item(quote.item.id, count), price, quote.purpose, quote.bot)
    }

    /**
     * A noted delivery prevents another purchase but cannot release the physical working fallback yet.
     */
    fun retained(bot: Bot): Map<Int, Int> = current(bot).mapNotNull { need ->
        val fallback = need.fallback ?: return@mapNotNull null
        if (bot.totalItemAmount(fallback) < 1 || need.alternatives.any {
                usableNow(bot, it) && bot.itemTracker.count(it) > 0
            }) null else fallback to 1
    }.toMap()

    fun toolKey(ids: Collection<Int>): String = when {
        ids.any { id -> Axe.entries.any { it.id == id } } -> "tool:axe"
        ids.any { it in Pickaxe.ID_TO_PICKAXE } -> "tool:pickaxe"
        else -> "tool:${ids.minOrNull()}"
    }

    fun toolOrder(ids: Collection<Int>): List<Int> {
        val axes = Axe.entries.associate { it.id to it.chances.values.sumOf { (min, max) -> min + max } }
        val picks = Pickaxe.entries.associate { it.id to -it.speed }
        return ids.sortedWith(compareByDescending<Int> { axes[it] ?: picks[it] ?: 0 }.thenBy { it })
    }
}
