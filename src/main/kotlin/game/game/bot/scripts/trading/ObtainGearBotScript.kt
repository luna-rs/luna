package game.bot.scripts.trading

import api.bot.script.DynamicBotScript
import engine.bot.gear.BotGearProgression
import engine.controllers.Controllers.inWilderness
import io.luna.game.model.mob.bot.Bot

/**
 * Makes one bounded shopping attempt for recently unlocked gear after the current activity finishes.
 * Unavailable upgrades remain on the wanted list for later merchanting decisions.
 *
 * @author lare96
 */
class ObtainGearBotScript(bot: Bot, ids: Collection<Int>) : DynamicBotScript(bot) {

    /**
     * Gear discovered while this errand is pending, in discovery order.
     */
    private val wanted = LinkedHashSet(ids)

    /**
     * Updates the pending upgrades without scheduling another trip or retaining superseded gear.
     */
    fun replace(ids: Collection<Int>) {
        wanted.clear()
        wanted.addAll(ids)
    }

    override suspend fun run(): Boolean {
        if (bot.inWilderness()) return true
        val attempt = wanted.toSet()
        val missing = BotGearProgression.refreshUpgrades(bot, attempt)
        if (missing.isNotEmpty()) handler.shop.obtainWantedItems(missing)
        wanted.removeAll(attempt)
        return wanted.isEmpty()
    }
}
