package api.bot.action.trading

import api.predef.*
import engine.bot.gear.BotEquipmentPreferences
import engine.bot.economy.BotProductionUses
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.def.ItemEconomyDefinition
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
     * Captures personality and mood once. Broad catalogue preparation omits native exit probing; offered
     * items reuse the existing actor-specific recovery policy for a complete owned batch.
     */
    fun snapshot(bot: Bot, includeShops: Boolean = true): Quotes = Quotes(bot,
        bot.personality.intelligence, bot.emotions.isFeeling(EmotionType.GREEDY), includeShops)

    /**
     * Stable unit quotes for one prepared basket or negotiation, with shared shop-access and emotional state.
     *
     * @author lare96
     */
    class Quotes internal constructor(private val bot: Bot, private val intelligence: Double,
                                       private val greedy: Boolean, private val probeExits: Boolean) {
        private val references = HashMap<Int, Long>()
        private val liquidations = HashMap<Int, Long>()
        private val bids = HashMap<Pair<Int, Boolean>, Long>()
        private val asks = HashMap<Int, Long>()
        private val ownedQuantities by lazy(LazyThreadSafetyMode.NONE) {
            (bot.inventory.filterNotNull() + bot.bank.filterNotNull()).asSequence()
                .filter { !it.isDynamic && it.id != 995 && it.itemDef.isTradeable }
                .groupBy { it.itemDef.unnotedId.orElse(it.id) }.mapValues { (_, items) ->
                    items.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                }
        }
        private val nativeRecovery by lazy(LazyThreadSafetyMode.NONE) {
            BotProductionUses.recoverableValues(bot, ownedQuantities, operations = 1, includeRecentTrades = false)
        }

        /**
         * Resolves only this item against actual native recovery and funded market exits for the owned batch.
         */
        fun reference(id: Int, guide: Long): Long = references.getOrPut(id) {
            val quantity = if (probeExits) (ownedQuantities[id] ?: 1).coerceAtLeast(1) else 1
            val liquidation = if (!probeExits) 0L else maxOf(
                nativeRecovery[id] ?: BotProductionUses.recoverableValues(bot, mapOf(id to quantity), operations = 1, includeRecentTrades = false)[id] ?: 0L,
                BotMerchantMarket.fundedNet(bot, id, quantity) / quantity)
            liquidations[id] = liquidation
            maxOf(guide, liquidation)
        }

        /**
         * Retains separate personal-use and resale quotes for each offered commodity. Personal needs use
         * the ordinary captured buying strategy; all resale stock receives seventy-five to ninety percent of reference.
         */
        fun buy(id: Int, guide: Long, useful: Boolean = false,
                strategy: BotTradePrice = BotTradePrice.EXACT): Long = bids.getOrPut(id to useful) {
            val reference = reference(id, guide)
            if (useful) BotMarketExperience.buy(bot, id, strategy.applyBuy(reference),
                BotTradeIntentions.productionLimit(bot, id)?.maximumUnitPrice ?: Long.MAX_VALUE)
            else BotUtilityPurchases.price(bot, id, resaleBid(reference, intelligence, greedy, rand().nextDouble()))
        }

        /**
         * Starts resale asks above reference. Experienced unsold stock may discount toward independently
         * accessible liquidation proceeds while retaining the merchant's margin on that alternative.
         */
        fun sell(id: Int, guide: Long, strategy: BotTradePrice): Long = asks.getOrPut(id) {
            val reference = reference(id, guide)
            val margin = 0.05 + intelligence.coerceIn(0.0, 1.0) * 0.10 + if (greedy) 0.10 else 0.0
            val minimum = maxOf(scaled(reference, 1.0 + margin),
                if (reference == Long.MAX_VALUE) reference else reference + 1)
            BotMarketExperience.sell(bot, id,
                maxOf(strategy.applySell(scaled(reference, 1.20 + margin)), minimum),
                scaled(liquidations.getValue(id), 1.0 + margin))
        }
    }

    /**
     * Estimates the best available unit value after alchemy's rune costs and accessible GP shop restrictions.
     */
    fun reference(bot: Bot, id: Int, guide: Long): Long = snapshot(bot).reference(id, guide)

    /**
     * Computes raw spell arithmetic; executable recovery additionally requires the shared native policy.
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
     * Quotes tradeable resale stock or a separately identified personal requirement.
     */
    fun buy(bot: Bot, id: Int, guide: Long, useful: Boolean = false): Long = snapshot(bot).buy(id, guide, useful)

    /**
     * Recognizes common supplies and established markets from actual samples, independently of popularity seeds.
     * Notes use their unnoted commodity's history. Sparse non-supply markets gain protection gradually.
     */
    fun marketLiquidity(id: Int): Double {
        val base = itemDef(id).unnotedId.orElse(id)
        val samples = world.economy.prices[base]?.samples ?: 0L
        return liquidity(samples, BotTradeSelection.supplyPriority(base) > 0)
    }

    /**
     * Identifies markets whose advertised prices should not receive another merchant bargaining discount.
     */
    fun liquidMarket(id: Int): Boolean = marketLiquidity(id) >= 1.0

    /**
     * Treats supplies as readily resalable and gives other items full protection after 1,000 observed samples.
     */
    internal fun liquidity(samples: Long, supply: Boolean): Double =
        if (supply) 1.0 else (samples.coerceAtLeast(0L).toDouble() / 1_000.0).coerceAtMost(1.0)

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
     * Quotes every resale category at seventy-five to ninety percent of reference. Personality and one captured
     * sample vary the margin, while integer arithmetic preserves bounds for large prices without floating overflow.
     * For reference values below four coins, the nearest positive whole-coin quote may exceed ninety percent.
     */
    internal fun resaleBid(reference: Long, intelligence: Double, greedy: Boolean, sample: Double): Long {
        val value = reference.coerceAtLeast(1)
        val basisPoints = (8_000 + sample.coerceIn(0.0, 1.0) * 1_000 -
                intelligence.coerceIn(0.0, 1.0) * 300 - if (greedy) 200 else 0).toInt().coerceIn(7_500, 9_000)
        val minimum = percentage(value, 7_500, true)
        val maximum = maxOf(minimum, percentage(value, 9_000, false))
        return percentage(value, basisPoints, true).coerceIn(minimum, maximum)
    }

    /**
     * Multiplies by a bounded basis-point fraction without overflowing the signed long range.
     */
    private fun percentage(value: Long, basisPoints: Int, roundUp: Boolean): Long =
        value / 10_000 * basisPoints +
                (value % 10_000 * basisPoints + if (roundUp) 9_999 else 0) / 10_000

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
