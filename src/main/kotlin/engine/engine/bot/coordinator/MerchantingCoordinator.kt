package engine.bot.coordinator

import api.bot.action.trading.BotTradePurpose
import api.bot.action.trading.TradeBotScript
import api.predef.*
import api.predef.ext.*
import game.bot.scripts.BuyFromStoreBotScript
import game.bot.scripts.trading.SellToGeneralStoreBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.util.RandomUtils

/**
 * Coordinates merchanting-related activities for bots.
 *
 * This coordinator is responsible for assigning scripts that allow a bot to participate in the server economy through
 * activities such as purchasing items from shops and, eventually, trading with other players or bots.
 *
 * Merchanting behavior itself is implemented by the individual scripts pushed onto the bot's script stack. This keeps
 * the coordinator lightweight and allows merchanting activities to be expanded without coupling their logic together.
 *
 * @author lare96
 */
object MerchantingCoordinator : BotCoordinator {

    override fun accept(bot: Bot) {
        val likesMerchanting = bot.preferences.likesActivity(BotActivity.MERCHANTING)
        val lovesMerchanting = bot.preferences.lovesActivity(BotActivity.MERCHANTING)
        val wantedItemSurplus = bot.preferences.wantedItemsToTrim()
        if (wantedItemSurplus.isNotEmpty()) {
            // Start trading script, or sell items to store.
            val purpose =
                if ((randBoolean() && lovesMerchanting) || (RandomUtils.roll(1 of 3) && likesMerchanting)) {
                    BotTradePurpose.MERCHANT_SELL
                } else {
                    BotTradePurpose.SELL
                }
            val generalStore = world.shops.get("General Store")
            val spaceLeft = generalStore?.items?.let { it.capacity() - it.size() } ?: 0
            val isDumbOrGreedy = bot.personality.isDumb || (bot.personality.isIntelligent&& bot.emotions.isFeeling(
                EmotionType.GREEDY))
            if (generalStore != null && spaceLeft > 0 && randBoolean() && isDumbOrGreedy) {
                bot.scriptStack.push(SellToGeneralStoreBotScript(bot))
            } else {
                bot.scriptStack.push(TradeBotScript(bot, purpose))
            }
        } else if (bot.preferences.wantedItemsToAcquire().isNotEmpty()) {
            // Start trading script, or buy items from store.
            val purpose =
                if ((randBoolean() && lovesMerchanting) || (RandomUtils.roll(1 of 3) && likesMerchanting)) {
                    BotTradePurpose.MERCHANT_BUY
                } else {
                    BotTradePurpose.BUY
                }
            bot.scriptStack.push(TradeBotScript(bot, purpose))
        } else {
            // Otherwise make generic purchases from registered shops.
            bot.scriptStack.push(BuyFromStoreBotScript(bot))
        }
    }
}