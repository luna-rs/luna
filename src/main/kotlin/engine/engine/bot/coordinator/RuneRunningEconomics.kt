package engine.bot.coordinator

import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.NativeOutputDemand
import api.predef.SKILL_RUNECRAFTING
import api.predef.runecrafting
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import kotlin.math.roundToInt

/**
 * Quotes paid delivery work from native buying visits and cached economy prices.
 * A price can guide a decision. Only owned notes and coins can fund a reservation.
 *
 * @author lare96
 */
internal object RuneRunningEconomics {
    private const val MAX_UNIT_PRICE = 1_000_000L
    private const val MAX_FEE = 2_000

    fun minimumFee(bot: Bot, altar: Altar, introductory: Boolean): Int {
        val profitInterest = (bot.preferences.activities[BotActivity.GENERAL_PROFIT] ?: 0.0)
            .takeIf(Double::isFinite)?.coerceIn(0.0, 1.0) ?: 0.0
        val rate = if (introductory) 0.5 else 0.75 + profitInterest * 0.75
        return RuneRunningCoordinator.fare(altar) + (routeWork(altar) * rate).roundToInt()
    }

    fun routeWork(altar: Altar): Int = when (altar) {
        Altar.AIR, Altar.EARTH, Altar.FIRE, Altar.BODY -> 20
        Altar.MIND, Altar.WATER -> 35
        Altar.COSMIC, Altar.LAW, Altar.CHAOS -> 55
        Altar.NATURE, Altar.DEATH -> 80
    }

    fun forecast(bot: Bot, route: RuneRunningCoordinator.Route, checkNativeDemand: Boolean = false): Long {
        val rune = route.rune
        val output = RuneRunningCoordinator.BATCH * rune.outputMultiplier(bot.runecrafting.level)
        val economy = bot.world.economy
        val inputPrice = economy.getPrice(route.essenceId).coerceIn(1, MAX_UNIT_PRICE)
        val bid = if (checkNativeDemand) {
            NativeOutputDemand.depth(bot, rune.id).firstOrNull { it.quantity >= output && it.unitPrice > 0 }?.unitPrice
        } else null
        val observed = economy.getRecentTradeReference(rune.id)?.takeIf { it.independentPairs() >= 2 }?.price()
            ?.takeIf { it.isFinite() && it > 0.0 }?.toLong()
        val outputValue = (bid ?: observed)?.coerceIn(0, MAX_UNIT_PRICE)
            ?: (economy.getPrice(rune.id).coerceIn(0, MAX_UNIT_PRICE) * 2 / 3)
        return outputValue * output - inputPrice * RuneRunningCoordinator.BATCH
    }

    fun quoteFee(bot: Bot, route: RuneRunningCoordinator.Route, minimum: Int, coins: Long,
                 training: Boolean): Int? {
        val fare = RuneRunningCoordinator.fare(route.altar)
        val trainingInterest = training && BotCombatActionHandler.wantsToTrain(bot, SKILL_RUNECRAFTING) &&
            (bot.preferences.isSkiller || SKILL_RUNECRAFTING in bot.preferences.skills)
        return fee(minimum, forecast(bot, route, checkNativeDemand = true), fare, coins, trainingInterest,
            routeWork(route.altar))
    }

    /**
     * Shares part of a positive margin for saved travel. A trainer's limited purse can cover an XP-motivated fee.
     */
    internal fun fee(minimum: Int, margin: Long, fare: Int, coins: Long, training: Boolean,
                     routeWork: Int): Int? {
        if (minimum <= fare || minimum > MAX_FEE || coins <= fare) return null
        val fund = ((coins - fare) / 10).coerceAtMost(MAX_FEE.toLong())
        val profitShare = ((margin - fare).coerceAtLeast(0) / 2).coerceAtMost(MAX_FEE.toLong())
        val trainingAllowance = if (training) ((coins - fare) / 30).coerceAtMost((fare + routeWork * 3).toLong())
            else 0
        val maximum = minOf(fund, maxOf(profitShare, trainingAllowance))
        return if (maximum < minimum) null
        else (minimum + (maximum - minimum) / 3).toInt()
    }
}
