package engine.bot.coordinator

import api.bot.script.BotScript
import api.predef.*
import game.bot.scripts.FillWaterBotScript
import game.bot.scripts.MakeCrystalKeyBotScript
import game.bot.scripts.OpenCrystalChestBotScript
import game.obj.resource.fillable.WaterResource
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.time.Duration.Companion.minutes

/**
 * Selects useful general activities from supplies the bot already owns.
 *
 * Owned crystal keys are used at a loaded chest, matched halves can be assembled, and empty water containers
 * are filled toward their wanted-stock target. These tasks do not depend on a Crafting or combat preference.
 * Minigames retain their separate category. No work is queued when supplies or applicable content are absent.
 *
 * @author lare96
 */
object GeneralActivityCoordinator : BotCoordinator {
    /** Default total water stock per container type when no wanted-item definition supplies a target. */
    private const val DEFAULT_WATER_TARGET = 1_000
    /** Loaded chest positions, discovered once rather than scanning all world objects on every decision. */
    private val chestPositions by lazy {
        world.objects.filter { it.id == OpenCrystalChestBotScript.CHEST }.map { it.position }
    }

    override fun accept(bot: Bot) {
        getScript(bot)?.let { bot.scriptStack.push(it) }
    }

    /** Counts unnoted stock across bank and inventory using widened arithmetic. */
    private fun owned(bot: Bot, id: Int) = bot.bank.computeAmountForId(id).toLong() +
        bot.inventory.computeAmountForId(id)

    /**
     * Chooses a supported activity with owned inputs and a bounded session duration.
     * Filled-water targets preserve the bot's configured quantity, while available empties limit each task.
     *
     * @param bot The bot whose supplies determine readiness.
     * @return A ready general activity, or null when none can currently run.
     */
    internal fun getScript(bot: Bot): BotScript? {
        val duration = if (bot.preferences.likesActivity(BotActivity.GENERAL_ACTIVITIES))
            rand(100, 350).minutes else rand(45, 120).minutes
        if (owned(bot, OpenCrystalChestBotScript.KEY) > 0) {
            chestPositions.minByOrNull { it.computeLongestDistance(bot.position) }?.let {
                return OpenCrystalChestBotScript(bot, it, duration)
            }
        }
        MakeCrystalKeyBotScript(bot, duration).let { if (it.isEligible()) return it }
        for ((empty, filled) in WaterResource.FILLABLES) {
            val emptyStock = owned(bot, empty)
            val filledStock = owned(bot, filled)
            val wanted = bot.preferences.getWantedItem(filled)
            val desired = wanted?.target() ?: DEFAULT_WATER_TARGET
            if (emptyStock > 0 && filledStock < desired) {
                val reachable = minOf(desired.toLong(), filledStock + emptyStock).toInt()
                return FillWaterBotScript(bot, empty, reachable, duration)
            }
        }
        return null
    }
}
