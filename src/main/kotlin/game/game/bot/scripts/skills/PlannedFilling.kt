package game.bot.scripts.skills

import api.bot.script.ProductionPlan
import game.bot.scripts.FillWaterBotScript
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Builds an owned-container prerequisite for the current finite goal. The parent is paused on its existing stack
 * entry, preserving the same persisted plan, spent coins and absolute deadline while this zero-purchase child runs.
 */
internal fun plannedFilling(bot: Bot, plan: ProductionPlan, empty: Int, filled: Int,
                           maximumUnits: Long, duration: Duration,
                           source: FillWaterBotScript.Source = FillWaterBotScript.Source.WATER): FillWaterBotScript? {
    val now = System.currentTimeMillis()
    if (plan.deadline <= now || duration <= Duration.ZERO || maximumUnits < 1 ||
        empty !in source.emptyIds || source.resource.getFilled(empty) != filled) return null
    fun owned(id: Int) = bot.inventory.computeNonDynamicAmountForId(id).toLong() +
        bot.bank.computeNonDynamicAmountForId(id)
    val current = owned(filled)
    val missing = (plan.target(filled).toLong() - current).coerceAtLeast(0L)
    val amount = minOf(missing, owned(empty), maximumUnits)
    if (amount < 1) return null
    return FillWaterBotScript(bot, empty, (current + amount).toInt(),
        minOf(duration, (plan.deadline - now).milliseconds), source = source, goalDeadline = plan.deadline)
}
