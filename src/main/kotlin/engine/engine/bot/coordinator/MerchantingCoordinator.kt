package engine.bot.coordinator

import game.bot.scripts.BuyFromStoreBotScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator

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
        // TODO Add trading script(s) here once bot-to-bot trading is ready to participate in the merchanting cycle.

        // Give the bot an opportunity to satisfy wanted items or make personality-driven purchases from registered shops.
        bot.scriptStack.push(BuyFromStoreBotScript(bot))
    }
}