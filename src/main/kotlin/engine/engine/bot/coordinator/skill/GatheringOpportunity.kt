package engine.bot.coordinator.skill

import api.bot.action.trading.ProfitGatheringDemand
import api.bot.script.BotScript
import api.bot.script.ProfitGatheringGoal
import api.bot.zone.SubZone
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * An eligible native gathering job and its demand quote. Only the chosen job promises supply.
 *
 * @author lare96
 */
internal class GatheringOpportunity(
    val quote: ProfitGatheringDemand.Quote,
    private val locations: List<SubZone>,
    private val bot: Bot,
    private val duration: Duration,
    private val outputs: Set<Int> = setOf(quote.itemId),
    private val reorder: Boolean = false,
    private val script: (Duration, MutableList<SubZone>) -> BotScript
) {
    fun start(zones: MutableList<SubZone>, maximumDuration: Duration = duration): BotScript {
        val destinations = when {
            !reorder -> locations
            bot.personality.isDextrous -> locations.sortedBy { bot.position.computeLongestDistance(it.inside) }
            else -> locations.shuffled()
        }
        zones.clear()
        zones.addAll(destinations)
        val time = minOf(duration, maximumDuration)
        return script(time, zones).also {
            it.profitGathering = ProfitGatheringGoal.create(bot, quote, time, outputs)
        }
    }
}
