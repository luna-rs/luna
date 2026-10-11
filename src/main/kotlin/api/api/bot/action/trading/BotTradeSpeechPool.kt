package api.bot.action.trading

import api.bot.action.trading.BotTradeSpeechPool.BotTradeSpeech
import io.luna.game.model.mob.bot.speech.BotSpeechPool

/**
 * A speech pool containing dialogue used by bots during trading activities.
 *
 * Loads trading dialogue from `trading.jsonc` and organizes it by [BotTradeSpeech].
 * @author lare96
 */
object BotTradeSpeechPool :
    BotSpeechPool<BotTradeSpeech>("trading.jsonc", BotTradeSpeech::class.java) {

    /**
     * Represents the categories of trading dialogue available to bots.
     */
    enum class BotTradeSpeech {
        /** Dialogue used when a bot is looking to buy items. */
        BUYING,

        /** Dialogue used when a bot is looking to sell items. */
        SELLING,

        /** Dialogue used when a merchant bot is looking to buy items. */
        MERCHANT_BUYING,

        /** Dialogue used when a merchant bot is looking to sell items. */
        MERCHANT_SELLING,

        /** Dialogue used to advertise the price of an individual item. */
        PER_ITEM
    }
}