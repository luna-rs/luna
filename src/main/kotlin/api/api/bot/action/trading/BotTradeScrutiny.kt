
package api.bot.action.trading

import api.predef.*
import com.google.common.collect.HashMultiset
import engine.trade.TradeItemContainer
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Defines how carefully a bot validates another player's trade offer before accepting it.
 *
 * Higher scrutiny levels verify expected item quantities or trade value, while lower levels perform
 * less thorough checks or accept offers without validation. The selected level is influenced by the
 * bot's personality, merchanting preferences, and feelings toward the other player.
 *
 * Buying checks compare expected items against the received offer. Selling checks compare the
 * received item's market value against an expected trade value.
 *
 * @property buying The validation function for items the bot expects to receive when buying.
 * @property selling The validation function for the value the bot expects to receive when selling.
 * @author lare96
 */
enum class BotTradeScrutiny(
    val buying: (List<Item>, Bot, List<Item>) -> Boolean, // bot scrutinizing expected items, bot scrutinizing, other bot offering
    val selling: (Long, Bot, List<Item>) -> Boolean, // bot scrutinizing expected value, bot scrutinizing, other bot offering
) {

    /**
     * The strictest trade scrutiny level.
     *
     * Buying requires every expected item id and its total quantity to be present in the received
     * offer. Duplicate item requirements are combined using a [HashMultiset], while the received
     * offer is checked using normal trade-container stacking rules.
     *
     * Selling requires the received items to be worth at least the expected trade value.
     */
    PARANOID(
        buying = { expected, bot, receiving ->
            val expectedSet = HashMultiset.create<Int>(expected.size)
            expected.forEach { expectedSet.add(it.id, it.amount) }

            val receivingContainer = TradeItemContainer()
            receivingContainer.addAll(receiving)
            expectedSet.entrySet().all { Item(it.element, it.count) in receivingContainer }
        },
        selling = { expected, bot, receiving ->
            receiving.sumOf { world.economy.getPrice(it.id) * it.amount.toLong() } >= expected
        }
    ),

    /**
     * A high scrutiny level that occasionally performs weaker validation.
     *
     * Usually delegates to [PARANOID]. There is a one-in-64 chance of using [TRUSTWORTHY] instead,
     * or an intelligence-dependent chance of using [NORMAL]. Less intelligent bots are more likely
     * to perform the weaker checks.
     */
    SMART(
        buying = { expected, bot, receiving ->
            when {
                rand(64) == 0 -> TRUSTWORTHY.buying(expected, bot, receiving)
                rand(1.0 - bot.personality.intelligence) -> NORMAL.buying(expected, bot, receiving)
                else -> PARANOID.buying(expected, bot, receiving)
            }
        },
        selling = { expected, bot, receiving ->
            when {
                rand(64) == 0 -> TRUSTWORTHY.selling(expected, bot, receiving)
                rand(1.0 - bot.personality.intelligence) -> NORMAL.selling(expected, bot, receiving)
                else -> PARANOID.selling(expected, bot, receiving)
            }
        },
    ),

    /**
     * The standard trade scrutiny level.
     *
     * Buying requires every expected item id to appear in the received offer, but ignores
     * differences in item quantities.
     *
     * Selling requires the received value to exceed the expected value multiplied by
     * [BotTradeManager.NORMAL_SELLING_ATTENTION].
     */
    NORMAL(
        buying = { expected, bot, receiving ->
            val receivingIds = receiving.map { it.id }
            expected.all { item -> item.id in receivingIds }
        },
        selling = { expected, bot, receiving ->
            receiving.sumOf { world.economy.getPrice(it.id) * it.amount.toLong() } >
                    expected * BotTradeManager.NORMAL_SELLING_ATTENTION
        }
    ),

    /**
     * A low scrutiny level that performs only basic offer validation.
     *
     * Buying normally checks whether the received offer contains at least as many occupied
     * trade slots as the expected offer. Item identities and quantities are not compared.
     *
     * Less intelligent bots may instead delegate to [DUMB] and skip validation entirely.
     * Selling only requires the received value to exceed the expected value multiplied by
     * [BotTradeManager.TRUSTWORTHY_SELLING_ATTENTION].
     */
    TRUSTWORTHY(
        buying = { expected, bot, receiving ->
            when {
                rand(1.0 - bot.personality.intelligence) -> DUMB.buying(expected, bot, receiving)
                else -> {
                    val expectedContainer = TradeItemContainer()
                    val receivingContainer = TradeItemContainer()
                    expectedContainer.addAll(expected)
                    receivingContainer.addAll(receiving)
                    receivingContainer.size() >= expectedContainer.size()
                }
            }
        },
        selling = { expected, bot, receiving ->
            receiving.sumOf { world.economy.getPrice(it.id) * it.amount.toLong() } >
                    expected * BotTradeManager.TRUSTWORTHY_SELLING_ATTENTION
        }
    ),

    /**
     * The lowest trade scrutiny level.
     *
     * Always accepts the received offer without checking item ids, quantities, or trade value.
     */
    DUMB(
        buying = { expected, bot, container -> true },
        selling = { expected, bot, receiving -> true }
    );

    companion object {

        /**
         * Resolves the scrutiny level a bot uses when trading with another player.
         *
         * First determines a baseline from the bot's intelligence, sociability, and merchanting
         * preferences. Intelligent or merchanting-focused bots generally receive stricter scrutiny,
         * while unintelligent bots may receive weaker scrutiny depending on their sociability.
         *
         * The bot's feelings toward [other] provide a second scrutiny level. Disliked players may
         * require more careful validation, while liked players are assigned more trusting levels.
         *
         * The stricter of the two levels is returned, so positive feelings cannot reduce the bot's
         * baseline scrutiny. Enum ordering determines strictness, with [PARANOID] being the strictest.
         *
         * @param bot The bot whose scrutiny level is being determined.
         * @param other The player trading with the bot.
         * @return The stricter scrutiny level derived from personality and player feelings.
         */
        fun resolveScrutinyFor(bot: Bot, other: Player): BotTradeScrutiny {
            // First determine desired scrutiny based on personality.
            val scrutinyActivity = when {
                bot.preferences.lovesActivity(BotActivity.MERCHANTING) -> PARANOID
                bot.personality.isIntelligent && bot.personality.isSocial -> PARANOID
                bot.preferences.likesActivity(BotActivity.MERCHANTING) || bot.personality.isIntelligent -> SMART
                bot.personality.isDumb && bot.personality.isSocial -> TRUSTWORTHY
                bot.personality.isDumb && !bot.personality.isSocial -> DUMB
                else -> NORMAL
            }
            // Then determine desired scrutiny based on feelings about the participant.
            val scrutinyPlayer = when {
                bot.preferences.hatesPlayer(other.username) -> SMART
                bot.preferences.dislikesPlayer(other.username) -> NORMAL
                bot.preferences.lovesPlayer(other.username) -> DUMB
                bot.preferences.likesPlayer(other.username) -> TRUSTWORTHY
                else -> scrutinyActivity
            }
            // Whichever scrutiny level is higher is used (Enum ordered from highest (0) to lowest (enum size) scrutiny).
            return if (scrutinyPlayer.ordinal <= scrutinyActivity.ordinal) scrutinyPlayer else scrutinyActivity
        }
    }

    /**
     * Validates a received trade offer using this scrutiny level.
     *
     * Buying purposes validate the received items against [expectedItems]. Selling purposes
     * validate the received market value against [expectedValue]. The actual checks depend on
     * this scrutiny level's [buying] and [selling] functions.
     *
     * @param purpose The purpose of the trade being validated.
     * @param expectedValue The expected received value when selling.
     * @param expectedItems The items the bot expects to receive when buying.
     * @param bot The bot performing the trade validation.
     * @param receivingItems The items currently offered by the other player.
     * @return `true` if the received offer passes this scrutiny level's validation.
     */
    fun applyScrutiny(purpose: BotTradePurpose,
                      expectedValue: Long,
                      expectedItems: List<Item>,
                      bot: Bot,
                      receivingItems: List<Item>): Boolean {
        return when (purpose) {
            BotTradePurpose.BUY, BotTradePurpose.MERCHANT_BUY -> buying(expectedItems, bot, receivingItems)
            BotTradePurpose.SELL, BotTradePurpose.MERCHANT_SELL -> selling(expectedValue, bot, receivingItems)
        }
    }
}
