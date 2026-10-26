package engine.bot.coordinator

import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.predef.*
import game.bot.scripts.TreasureTrailBotScript
import game.bot.scripts.UseLampBotScript
import game.item.experienceLamp.ExperienceLampInterface.InterfaceSkill
import game.bot.scripts.FillWaterBotScript
import game.bot.scripts.FillWaterBotScript.Source
import game.bot.scripts.MakeCrystalKeyBotScript
import game.bot.scripts.OpenCrystalChestBotScript
import game.bot.scripts.SearchNestBotScript
import game.skill.woodcutting.searchNest.Nest
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.time.Duration.Companion.minutes

/**
 * Selects useful general activities from supplies the bot already owns.
 *
 * Owned crystal keys are used at a loaded chest, matched halves can be assembled, and empty water containers
 * are filled toward their wanted-stock target. Wanted buckets of milk and sand use the same native filling task.
 * Owned unsearched bird nests can also be opened and their contents banked. Owned experience lamps are redeemed
 * in a preferred supported skill. These tasks do not depend on a Crafting or combat preference.
 * Owned clue scrolls and caskets across all three tiers enable bounded Treasure Trails with an owned spade.
 * Minigames retain their separate category. No work is queued when supplies or applicable content are absent.
 *
 * @author lare96
 */
object GeneralActivityCoordinator : BotCoordinator {
    /**
     * Default total water stock per container type when no wanted-item definition supplies a target.
     * Other resources are prepared only when the bot has an explicit wanted-stock target.
     */
    private const val DEFAULT_WATER_TARGET = 1_000
    /**
     * Loaded chest positions, discovered once rather than scanning all world objects on every decision.
     */
    private val chestPositions by lazy {
        world.objects.filter { it.id == OpenCrystalChestBotScript.CHEST }.map { it.position }
    }

    override fun accept(bot: Bot) {
        getScript(bot)?.let { bot.scriptStack.push(it) }
    }

    /**
     * Counts unnoted stock across bank and inventory using widened arithmetic.
     */
    private fun owned(bot: Bot, id: Int) = bot.bank.computeAmountForId(id).toLong() +
        bot.inventory.computeAmountForId(id)

    /**
     * Chooses a supported activity with owned inputs and a bounded session duration.
     * Filling targets preserve the bot's configured quantity, while available empties limit each task.
     * Ready sources share selection so a water deficit cannot permanently suppress milk or sand requests.
     *
     * @param bot The bot whose supplies determine readiness.
     * @return A ready general activity, or null when none can currently run.
     */
    internal fun getScript(bot: Bot): BotScript? {
        val duration = if (bot.preferences.likesActivity(BotActivity.GENERAL_ACTIVITIES))
            rand(100, 350).minutes else rand(45, 120).minutes
        if (owned(bot, UseLampBotScript.LAMP) > 0) {
            val eligible = InterfaceSkill.values().filter { BotCombatActionHandler.wantsToTrain(bot, it.skill) }
            val preferred = eligible.filter { it.skill in bot.preferences.skills }
            val skill = preferred.randomOrNull() ?: eligible.randomOrNull()
            if (skill != null) return UseLampBotScript(bot, skill, duration)
        }
        if (TreasureTrailBotScript.eligible(bot)) return TreasureTrailBotScript(bot)
        if (owned(bot, OpenCrystalChestBotScript.KEY) > 0) {
            chestPositions.minByOrNull { it.computeLongestDistance(bot.position) }?.let {
                return OpenCrystalChestBotScript(bot, it, duration)
            }
        }
        MakeCrystalKeyBotScript(bot, duration).let { if (it.isEligible()) return it }
        Nest.VALUES.filter { owned(bot, it.id) > 0 }.randomOrNull()?.let {
            return SearchNestBotScript(bot, it, duration)
        }
        val filling = buildList {
            for (source in Source.entries) {
                for (empty in source.emptyIds) {
                    val filled = requireNotNull(source.resource.getFilled(empty))
                    val desired = bot.preferences.getWantedItem(filled)?.target()
                        ?: if (source == Source.WATER) DEFAULT_WATER_TARGET else continue
                    val emptyStock = owned(bot, empty)
                    val filledStock = owned(bot, filled)
                    if (emptyStock > 0 && filledStock < desired) {
                        val reachable = minOf(desired.toLong(), filledStock + emptyStock).toInt()
                        add(FillWaterBotScript(bot, empty, reachable, duration, source))
                    }
                }
            }
        }
        return filling.randomOrNull()
    }
}
