package api.bot.action.trading

import api.predef.rand
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity

/**
 * Selects which marketplace visits may inspect a small sample of locally advertised goods.
 * Ordinary bots retain their existing choices unless their merchanting affinity admits this optional preference.
 * Callers select once per visit so banking and resume cannot repeatedly reroll the chance.
 *
 * @author lare96
 */
internal object BotMarketPreference {

    /**
     * Uses a zero-based percentile roll: liked merchanting gets 25 percent, loved merchanting 50 percent,
     * and dedicated merchants always inspect. Other ordinary bots never perform the additional scan.
     */
    fun shouldScan(bot: Bot, roll: Int = rand(99)): Boolean {
        val preferences = bot.preferences
        val chance = when {
            preferences.isMerchant -> 100
            preferences.lovesActivity(BotActivity.MERCHANTING) -> 50
            preferences.likesActivity(BotActivity.MERCHANTING) -> 25
            else -> 0
        }
        return chance == 100 || roll in 0 until chance
    }
}
