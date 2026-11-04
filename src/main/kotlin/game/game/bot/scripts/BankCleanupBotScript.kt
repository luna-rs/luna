package game.bot.scripts

import api.bot.action.BotBankSpace
import api.bot.script.DynamicBotScript
import io.luna.game.model.mob.bot.Bot

/**
 * Performs one bounded bank-maintenance visit before the brain selects another ordinary activity.
 *
 * @author lare96
 */
class BankCleanupBotScript(bot: Bot) : DynamicBotScript(bot) {

    override suspend fun run(): Boolean {
        BotBankSpace.maintain(bot, handler)
        return true
    }
}
