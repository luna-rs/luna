package api.bot.action.trading

import api.predef.*
import com.google.common.collect.ImmutableList
import engine.trade.TradeItemContainer
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotEmotion

/**
 * Describes how carefully a bot validates the other side of a trade before accepting.
 *
 * Higher scrutiny levels perform stricter checks against the items the bot expected to receive. Lower scrutiny levels
 * perform looser checks or blindly trust the trade screen. This lets intelligence, greed, and merchanting preference
 * influence how likely a bot is to notice missing items, swapped items, or changed amounts.
 *
 * The [searchFunction] returns `true` when the visible trade container is acceptable for this scrutiny level.
 *
 * @property range The normalized scrutiny range this level occupies. Higher values represent stricter checking.
 * @property searchFunction The trade validation function used against the other side's visible trade container.
 * @author lare96
 */
enum class TradeScrutiny(
    val range: ClosedFloatingPointRange<Double>,
    val searchFunction: (List<Item>).(Bot, ItemContainer) -> Boolean
) {

    /**
     * The strictest trade scrutiny level.
     *
     * Paranoid bots require every expected item id and amount to still be present in the offered container.
     */
    PARANOID(
        range = 0.85..1.0,
        searchFunction = {bot, container ->
            all { container.contains(it) }
        }
    ),

    /**
     * A high scrutiny level with a small chance of weaker validation.
     *
     * Smart bots usually perform paranoid checks. However, they can rarely behave as trustworthy bots, or fall back to
     * normal checks based on their intelligence.
     */
    SMART(
        range = 0.65..0.85,
        searchFunction = {bot, container ->
            when {
                rand(64) == 0 -> TRUSTWORTHY.searchFunction(this, bot, container)
                rand(1.0 - bot.personality.intelligence) -> NORMAL.searchFunction(this, bot, container)
                else -> PARANOID.searchFunction(this, bot, container)
            }
        }
    ),

    /**
     * The default trade scrutiny level.
     *
     * Normal bots check that each expected item id is still present, but do not verify the exact expected amounts.
     */
    NORMAL(
        range = 0.50..0.65,
        searchFunction = { bot, container ->
            all { container.contains(it.id) }
        }
    ),

    /**
     * A loose trade scrutiny level.
     *
     * Trustworthy bots may make a dumb mistake based on intelligence. Otherwise, they only check that the other side has
     * at least as many occupied trade slots as the expected offer, which can miss item swaps or amount changes.
     */
    TRUSTWORTHY(
        range = 0.25..0.50,
        searchFunction = { bot, container ->
            when {
                rand(1.0 - bot.personality.intelligence) -> DUMB.searchFunction(this, bot, container)
                else -> {
                    val expectedContainer = TradeItemContainer()
                    expectedContainer.addAll(this)
                    container.size() >= expectedContainer.size()
                }
            }
        }
    ),

    /**
     * The lowest trade scrutiny level.
     *
     * Dumb bots accept the visible trade contents without any validation.
     */
    DUMB(
        range = 0.0..0.25,
        searchFunction = { bot, container -> true }
    );

    companion object {

        /**
         * Resolves the scrutiny level a bot should use when checking trades.
         *
         * Merchanting preference, greed, and intelligence all influence the final scrutiny score. Bots that love
         * merchanting always use [PARANOID]. Bots that like merchanting become more careful, bots that hate merchanting
         * become less careful, greedy bots become more careless, and intelligent bots become more careful.
         *
         * @param bot The bot to resolve scrutiny for.
         * @return The scrutiny level this bot should use when validating trade offers.
         */
        fun resolveScrutinyFor(bot: Bot): TradeScrutiny {
            var base = 0.50 // 1.0 = paranoid, 0.0 = trustworthy.

            when {
                bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> return PARANOID
                bot.preferences.likesActivity(BotActivity.MERCHANTING) -> base *= 1.25
                bot.preferences.hatesActivity(BotActivity.MERCHANTING) -> base *= 0.75
            }

            if (bot.emotions.isFeeling(BotEmotion.EmotionType.GREEDY)) {
                base *= 0.75
            }

            base += (bot.personality.intelligence * 0.35)

            for (scrutiny in entries) {
                if (base in scrutiny.range) {
                    return scrutiny
                }
            }

            return NORMAL
        }
    }
}