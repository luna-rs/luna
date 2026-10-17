package engine.bot.coordinator

import api.bot.action.trading.BotTradePurpose
import api.predef.*
import api.predef.ext.*
import game.bot.scripts.trading.BuyFromStoreBotScript
import game.bot.scripts.trading.SellToStoreBotScript
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.util.RandomUtils

/**
 * Coordinates merchanting activities for bots.
 *
 * Selects economy-related scripts based on the bot's wanted-item stock, merchanting preferences,
 * personality, and emotions. Bots prioritize selling surplus items before acquiring wanted items,
 * falling back to general shop purchases when neither activity is available.
 *
 * Trading and shop interactions are performed by the scripts pushed onto the bot's script stack.
 * This coordinator only determines which activity the bot should attempt.
 *
 * @author lare96
 */
object MerchantingCoordinator : BotCoordinator {

    /**
     * Selects and schedules a merchanting activity for the specified bot.
     *
     * Bots with surplus wanted items attempt to sell them through player trading or a General Store.
     * General Store selling requires available shop space, a random chance, and either a dumb personality
     * or an intelligent personality currently feeling greedy.
     *
     * When no surplus exists, bots with wanted items to acquire attempt player trading. If neither
     * surplus nor acquisition candidates exist, the bot attempts generic purchases from shops.
     *
     * Player trading uses merchant-specific purposes more frequently when the bot likes or loves
     * merchanting. The selected script is pushed onto the bot's script stack for execution.
     *
     * @param bot The bot receiving the merchanting activity.
     */
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
            val isDumbOrGreedy = bot.personality.isDumb || (bot.personality.isIntelligent && bot.emotions.isFeeling(
                EmotionType.GREEDY))
            if (randBoolean() && isDumbOrGreedy) {
                bot.scriptStack.push(SellToStoreBotScript(bot))
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
            val isShopper = bot.personality.isIntelligent && !bot.preferences.hatesActivity(BotActivity.MERCHANTING) ||
                    bot.preferences.isMerchant
            if (rand(3) == 0 || isShopper && randBoolean()) {
                bot.scriptStack.push(BuyFromStoreBotScript(bot))
            } else {
                bot.scriptStack.push(TradeBotScript(bot, purpose))
            }
        } else {
            // Otherwise make generic purchases from registered shops.
            bot.scriptStack.push(BuyFromStoreBotScript(bot))
        }
    }
}
