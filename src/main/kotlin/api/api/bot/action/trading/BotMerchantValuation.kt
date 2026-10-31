package api.bot.action.trading

import api.predef.*
import engine.bot.gear.BotEquipmentPreferences
import game.skill.magic.Rune
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.def.ItemEconomyDefinition
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import kotlin.math.floor

/**
 * Prices merchant stock using its resale alternatives, personal tastes, and the merchant's judgment.
 *
 * Callers retain these quotes for a banking trip or trade attempt. Shop and alchemy values describe possible
 * liquidation value; obtaining a quote never sells an item or casts a spell.
 *
 * @author lare96
 */
object BotMerchantValuation {

    /**
     * Reads a guide without registering unused items as observed economy prices.
     */
    fun peekGuide(id: Int): Long {
        val definition = itemDef(id)
        val base = definition.unnotedId.orElse(id)
        val observed = world.economy.prices[base]?.price?.toLong()
        val initial = ItemEconomyDefinition.ALL.get(base).orElse(null)?.initialPrice
        return observed ?: initial?.toLong() ?: itemDef(base).value.toLong().coerceAtLeast(1)
    }

    /**
     * Captures personality, mood, rune costs and shop access once. Broad catalogue preparation omits shop
     * probing; offered items can use the full snapshot without scanning all item definitions.
     */
    fun snapshot(bot: Bot, includeShops: Boolean = true): Quotes = Quotes(bot,
        bot.personality.intelligence, bot.emotions.isFeeling(EmotionType.GREEDY), bot.magic.staticLevel,
        peekGuide(Rune.NATURE.id), peekGuide(Rune.FIRE.id), if (includeShops) world.shops.values()
            .filter { it.currency == Currency.COINS && it.botAccess.apply(bot) } else emptyList())

    /**
     * Stable unit quotes for one prepared basket or negotiation, with shared shop-access and emotional state.
     *
     * @author lare96
     */
    class Quotes internal constructor(private val bot: Bot, private val intelligence: Double,
                                       private val greedy: Boolean, private val magic: Int,
                                       private val nature: Long, private val fire: Long,
                                       private val shops: List<Shop>) {
        private val references = HashMap<Int, Long>()
        private val bids = HashMap<Int, Long>()
        private val asks = HashMap<Int, Long>()

        /**
         * Resolves only this item against the accessible shops and net alchemy proceeds.
         */
        fun reference(id: Int, guide: Long): Long = references.getOrPut(id) {
            val shop = shops.asSequence().filter { it.computeCanSell(id) }
                .map { it.computeSellValue(id, 1).toLong() }.maxOrNull() ?: 0L
            liquidationReference(guide, shop, itemDef(id).value, magic, nature, fire)
        }

        /**
         * Retains the sampled maximum unit payment for each offered commodity.
         */
        fun buy(id: Int, guide: Long, useful: Boolean = false): Long = bids.getOrPut(id) {
            bid(reference(id, guide), intelligence, greedy, BotEquipmentPreferences.isCosmetic(id),
                BotEquipmentPreferences.likes(bot, id), BotEquipmentPreferences.prefers(bot, id), useful,
                rand().nextDouble())
        }

        /**
         * Keeps resale asks above the reference even when the ordinary strategy discounts heavily.
         */
        fun sell(id: Int, guide: Long, strategy: BotTradePrice): Long = asks.getOrPut(id) {
            val reference = reference(id, guide)
            val margin = 0.05 + intelligence.coerceIn(0.0, 1.0) * 0.10 + if (greedy) 0.10 else 0.0
            maxOf(strategy.applySell(scaled(reference, 1.20 + margin)), scaled(reference, 1.0 + margin),
                if (reference == Long.MAX_VALUE) reference else reference + 1)
        }
    }

    /**
     * Estimates the best available unit value after alchemy's rune costs and accessible GP shop restrictions.
     */
    fun reference(bot: Bot, id: Int, guide: Long): Long = snapshot(bot).reference(id, guide)

    /**
     * Excludes unavailable alchemy spells and subtracts their rune costs from the unit proceeds.
     */
    internal fun liquidationReference(guide: Long, shop: Long, baseValue: Int, magic: Int,
                                      naturePrice: Long, firePrice: Long): Long {
        val type = when {
            magic >= AlchemyType.HIGH.level -> AlchemyType.HIGH
            magic >= AlchemyType.LOW.level -> AlchemyType.LOW
            else -> null
        }
        val alchemy = if (type == null) 0L else {
            val fire = if (type == AlchemyType.HIGH) 5 else 3
            val runes = naturePrice.coerceAtLeast(0).toDouble() + firePrice.coerceAtLeast(0).toDouble() * fire
            (baseValue * type.valueMultiplier - runes).coerceAtLeast(0.0).toLong()
        }
        return maxOf(guide, shop, alchemy, 1L)
    }

    /**
     * Quotes every tradeable item, protecting useful stock and cosmetics from resale discounts.
     */
    fun buy(bot: Bot, id: Int, guide: Long, useful: Boolean = false): Long = snapshot(bot).buy(id, guide, useful)

    /**
     * Prices a personal cosmetic purchase without applying a merchant's resale discount.
     */
    fun cosmeticBuy(bot: Bot, id: Int, guide: Long): Long = cosmeticBid(
        guide.coerceAtLeast(1), bot.personality.intelligence, bot.emotions.isFeeling(EmotionType.GREEDY),
        BotEquipmentPreferences.likes(bot, id), BotEquipmentPreferences.prefers(bot, id), rand().nextDouble(),
        bot.personality.social)

    /**
     * Keeps resale asking prices above the liquidation estimate instead of discounting wanted stock into a loss.
     */
    fun sell(bot: Bot, id: Int, guide: Long, strategy: BotTradePrice): Long = snapshot(bot).sell(id, guide, strategy)

    /**
     * Computes an explicit quote from a captured emotional state and random sample. Useful items and cosmetics
     * retain at least their reference value, while personal temptation can still raise the bid above it.
     */
    internal fun bid(reference: Long, intelligence: Double, greedy: Boolean, cosmetic: Boolean,
                     liked: Boolean, preferred: Boolean, useful: Boolean, sample: Double): Long {
        val judgment = intelligence.coerceIn(0.0, 1.0)
        val roll = sample.coerceIn(0.0, 1.0)
        val fraction = when {
            cosmetic -> {
                val taste = if (preferred) 0.25 else if (liked) 0.12 else 0.0
                val temptation = (1.0 - judgment) * (0.35 + taste + if (greedy) 0.35 else 0.0)
                0.45 + roll * 0.20 + taste * (1.0 - judgment * 0.5) + temptation
            }
            useful -> 0.45 + roll * 0.25 + (1.0 - judgment) * 0.10
            else -> (0.20 + roll * 0.30 - judgment * 0.10 - if (greedy) 0.05 else 0.0)
                .coerceIn(0.10, 0.50)
        }
        return scaled(reference, if (cosmetic || useful) fraction.coerceAtLeast(1.0) else fraction)
    }

    /**
     * Allows careful collectors to bargain while foolish or greedy collectors overpay for favorite pieces.
     */
    internal fun cosmeticBid(reference: Long, intelligence: Double, greedy: Boolean,
                             liked: Boolean, preferred: Boolean, sample: Double, social: Double = 0.0): Long {
        val judgment = intelligence.coerceIn(0.0, 1.0)
        val taste = if (preferred) 0.35 else if (liked) 0.15 else 0.0
        val fraction = 0.70 + sample.coerceIn(0.0, 1.0) * 0.30 + taste * (1.0 - judgment * 0.75) +
            (1.0 - judgment) * (0.20 + social.coerceIn(0.0, 1.0) * 0.15 + if (greedy) 0.30 else 0.0)
        return scaled(reference, fraction)
    }

    /**
     * Keeps positive prices representable when multiplication exceeds the signed long range.
     */
    private fun scaled(value: Long, multiplier: Double): Long =
        floor(value.coerceAtLeast(1).toDouble() * multiplier).toLong().coerceAtLeast(1)
}
