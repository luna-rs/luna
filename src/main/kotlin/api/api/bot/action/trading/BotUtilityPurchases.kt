package api.bot.action.trading

import api.attr.Attr
import api.bot.action.BotSupplySources
import api.predef.ext.totalItemAmount
import api.predef.ext.canUseWantedItem
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Values concrete reusable requirements against currently usable sources without imposing a world price floor.
 * Missing or failed alternatives leave scarce items on their ordinary funded buying and urgent-pricing paths.
 *
 * @author lare96
 */
object BotUtilityPurchases {
    private const val SOURCE_RETRY_MILLIS = 300_000L
    private val unavailableUntil = Attr.map<Int, Long>()

    private fun base(id: Int): Int = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)

    /**
     * Reads genuine activity and explicit ownership goals, excluding catalogue-only speculative utility demand.
     */
    fun target(bot: Bot, id: Int): Int {
        if (!BotReusableItems.isReusable(id)) return 0
        val canonical = base(id)
        if (!bot.canUseWantedItem(canonical)) return 0
        val wanted = bot.preferences.getWantedItem(canonical)
        val level = wanted?.let { if (it.skill() >= 0) bot.skill(it.skill()).staticLevel else bot.combatLevel }
        val explicit = if (wanted != null && wanted.maxLevel() >= 0 && level != null && level > wanted.maxLevel()) 0
            else BotTradeIntentions.explicitWantedTarget(bot, canonical)
        return maxOf(BotTradeIntentions.supplyDemand(bot, canonical)?.target() ?: 0,
            explicit)
    }

    /**
     * Counts current physical ownership before requesting another personally useful copy.
     */
    fun deficit(bot: Bot, id: Int): Int {
        val canonical = base(id)
        return (target(bot, canonical).toLong() - bot.totalItemAmount(canonical))
            .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Quotes one currently stocked GP alternative this account can afford through ordinary shop acquisition.
     * Actual HOME access, capacity and payment are rechecked by the native purchase after travel.
     */
    fun shopCeiling(bot: Bot, id: Int, now: Long = System.currentTimeMillis(),
                    refundableCoins: Long = 0L): Long? {
        if (!BotReusableItems.isReusable(id)) return null
        val canonical = base(id)
        val attributes = bot.attributes()
        if (attributes.has(unavailableUntil.attr) &&
            (attributes[unavailableUntil.attr][canonical] ?: 0L) > now) return null
        val source = BotSupplySources.quote(bot, canonical, 1) ?: return null
        val ownedCoins = bot.totalItemAmount(995).toLong()
        val refundable = refundableCoins.coerceIn(0L, Long.MAX_VALUE - ownedCoins)
        return source.maximumUnitPrice.takeIf { source.quantity >= 1 && it > 0L &&
            ownedCoins + refundable >= source.totalCoins }
    }

    /**
     * Defers a source that ordinary acquisition could not use, allowing the real need to seek scarce market supply.
     * Only reviewed ids can enter this bounded runtime map; a new quote becomes eligible after the cooldown.
     */
    fun shopFailed(bot: Bot, id: Int, now: Long = System.currentTimeMillis()) {
        if (BotReusableItems.isReusable(id))
            bot.attributes()[unavailableUntil.attr][base(id)] = now + SOURCE_RETRY_MILLIS
    }

    /**
     * Combines a live alternative with an existing production or spending ceiling without raising either limit.
     */
    fun price(bot: Bot, id: Int, proposed: Long, maximum: Long = Long.MAX_VALUE,
              refundableCoins: Long = 0L): Long =
        boundedPrice(proposed, maximum, shopCeiling(bot, id, refundableCoins = refundableCoins))

    internal fun boundedPrice(proposed: Long, maximum: Long, source: Long?): Long =
        proposed.coerceIn(1L, minOf(maximum, source ?: Long.MAX_VALUE).coerceAtLeast(1L))

    /**
     * Rechecks captured utility bids against current ownership, pending deliveries and genuinely usable sources.
     * Non-utility quotes and existing finite production budgets retain their ordinary admission rules.
     */
    fun constrain(quote: BotTradeItem): BotTradeItem? {
        if (!BotReusableItems.isReusable(quote.item.id)) return quote
        val count = minOf(quote.item.amount, deficit(quote.bot, quote.item.id))
        return if (count < 1) null else BotTradeItem(Item(quote.item.id, count),
            price(quote.bot, quote.item.id, quote.price), quote.purpose, quote.bot)
    }
}
