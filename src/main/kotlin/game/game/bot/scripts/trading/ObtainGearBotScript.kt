package game.bot.scripts.trading

import api.bot.script.DynamicBotScript
import api.bot.action.trading.BotGearDemand
import api.bot.action.trading.BotTradeIntentions
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
        if (missing.isNotEmpty()) {
            val targets = BotTradeIntentions.generatedEquipmentTargets(bot)
            val selected = (targets + missing.filter { BotTradeIntentions.hasLiteralSupplyDemand(bot, it) }).toSet()
            val coins = BotGearDemand.allowance(bot)
            val ceilings = selected.mapNotNull { id -> BotGearDemand.sourcePrice(bot, id, coins)?.let { id to it } }.toMap()
            if (ceilings.isNotEmpty()) handler.shop.obtainWantedItems(ceilings.keys.associateWith { 1 }, coins, ceilings)
        }
        wanted.removeAll(attempt)
        return wanted.isEmpty()
    }
}
