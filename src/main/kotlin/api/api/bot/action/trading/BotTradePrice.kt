package api.bot.action.trading

import api.predef.*
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import kotlin.math.floor

/**
 * Describes how aggressively a bot is willing to price trade offers.
 *
 * Each strategy stores two percentage ranges:
 * - [buy] is the amount the bot may offer above the guide price when buying.
 * - [sell] is the amount the bot may discount below the guide price when selling.
 *
 * For example, a [buy] value of `0.25` means the bot may offer 25% above guide price, while a [sell] value of `0.15`
 * means the bot may sell for 15% below guide price.
 */
enum class BotTradePrice(

    /**
     * The percentage range this bot may offer above guide price when buying.
     */
    val buy: ClosedFloatingPointRange<Double>,

    /**
     * The percentage range this bot may discount below guide price when selling.
     */
    val sell: ClosedFloatingPointRange<Double>
) {

    /**
     * The most aggressive trade strategy.
     *
     * Desperate buyers are willing to heavily overpay, while desperate sellers are willing to heavily undercut the
     * guide price to complete a trade quickly.
     */
    DESPERATE(
        buy = 0.65..1.0,
        sell = 0.30..0.65
    ),

    /**
     * A high urgency trade strategy.
     *
     * High urgency buyers offer a strong premium, while high urgency sellers apply a noticeable discount.
     */
    HIGH(
        buy = 0.25..0.50,
        sell = 0.15..0.30
    ),

    /**
     * A fixed guide-price trade strategy.
     *
     * Exact traders do not overpay when buying and do not discount when selling.
     */
    EXACT(
        buy = 0.0..0.0,
        sell = 0.0..0.0
    ),

    /**
     * A normal trade strategy.
     *
     * Average traders may offer a small premium when buying or a small discount when selling.
     */
    AVERAGE(
        buy = 0.0..0.15,
        sell = 0.0..0.075
    ),

    /**
     * A patient trade strategy.
     *
     * Low urgency traders only move slightly away from guide price and are less willing to sacrifice value for a fast
     * trade.
     */
    LOW(
        buy = 0.0..0.05,
        sell = 0.0..0.025
    );

    companion object {

        /**
         * Chooses a pricing strategy according to merchanting preferences, personality, and emotions.
         *
         * Conditions are checked in order: loved merchanting takes precedence, then liked activity
         * or intelligence, disliked activity or low intelligence, then greed.
         */
        fun computePriceStrategy(bot: Bot): BotTradePrice {
            return when {
                bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> BotTradePrice.LOW
                bot.preferences.likesActivity(BotActivity.MERCHANTING) ||
                        bot.personality.isIntelligent -> BotTradePrice.AVERAGE

                bot.preferences.hatesActivity(BotActivity.MERCHANTING) || bot.personality.isDumb -> BotTradePrice.DESPERATE
                bot.emotions.isFeeling(EmotionType.GREEDY) -> BotTradePrice.DESPERATE
                else -> BotTradePrice.HIGH
            }
        }
    }

    /**
     * Calculates a selling price by applying a random discount to the guide price.
     *
     * The discount is selected from [sell] and rounded down to the nearest whole coin.
     *
     * @param value The original guide price, in coins.
     * @return The adjusted selling price, in coins.
     */
    fun applySell(value: Long): Long {
        if (value <= 0) return 0
        val discount = if (sell.start == sell.endInclusive) sell.start else rand(sell)
        return (value - floor(value * discount).toLong()).coerceAtLeast(1)
    }

    /**
     * Calculates a buying price by applying a random premium to the guide price.
     *
     * The premium is selected from [buy] and rounded down to the nearest whole coin.
     *
     * @param value The original guide price, in coins.
     * @return The adjusted buying price, in coins.
     */
    fun applyBuy(value: Long): Long {
        if (value <= 0) return 0
        val premium = if (buy.start == buy.endInclusive) buy.start else rand(buy)
        val increase = floor(value * premium).toLong()
        return if (increase > Long.MAX_VALUE - value) Long.MAX_VALUE else value + increase
    }

}
