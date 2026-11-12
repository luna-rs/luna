package engine.bot.coordinator.skill

import api.bot.action.BotCombatActionHandler
import api.attr.Attr
import api.bot.zone.SubZone
import api.predef.SKILL_PRAYER
import api.predef.SKILL_MAGIC
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.util.RandomUtils

/**
 * Coordinates skilling activity for a [Bot].
 *
 * This coordinator selects a skill using weighted odds, creates the matching skilling script, and pushes that script
 * onto the bot's script stack.
 *
 * Skill selection is influenced by:
 * - The bot's current skill levels.
 * - The bot's preferred skills.
 * - Whether this coordinator is being used for training or profit.
 * - The set of supported [SkillingScriptFactory] instances.
 *
 * In training mode, lower-level skills are favored to encourage broader account progression. In profit
 * mode, higher-level skills are favored because they are more likely to unlock stronger money-making methods.
 *
 * Preferred skills receive an additional flat weight bonus in either mode.
 *
 * @param training Whether this coordinator should choose training scripts or profit scripts.
 * @author lare96
 */
class SkillingCoordinator(private val training: Boolean) : BotCoordinator {

    /*
     * TODO Bot money-making activities.
     *
     * Add richer money-making behavior once the economy system, item valuation, and item tagging are stable enough for
     * bots to make sensible profit-based decisions.
     *
     * Crafting and production:
     * - Make jewellery when the bot has useful gems, bars, moulds, and profit data.
     * - Make battlestaves when the bot has orbs, battlestaves, and profit data.
     *
     * Magic:
     * - Telegrab wines when the route, danger, rune supply, and expected profit make sense.
     * - High alch junk items for basic magic training, especially spare fletching products or low-value bank clutter.
     * - In money-making alch mode, compare economy value against high alchemy value before alching.
     *
     *
     * Trading post:
     * - Allow bots to request/buy needed items from anywhere in code.
     * - Require bots to physically visit the trading post to sell items.
     * - Use this as a sink/source bridge between bot activity, item demand, and the economy system.
     */

    companion object {

        /**
         * Base skilling session duration, in minutes.
         *
         * Bot traits may scale the final script duration beyond this range.
         */
        val BASE_SKILLING_DURATION_MINUTES = 30..180

        /**
         * Maximum level-based influence applied to a skill's selection weight.
         *
         * In training mode, this favors lower-level skills.
         * In profit mode, this favors higher-level skills.
         */
        private const val LEVEL_WEIGHT = 0.25

        /**
         * Flat selection-weight bonus applied to the bot's preferred skills.
         */
        private const val PREFERENCE_WEIGHT = 0.50

        /**
         * Gives eligible Magic training a larger share while retaining all other supported training choices.
         */
        private const val MAGIC_TRAINING_WEIGHT_MULTIPLIER = 2.0

        /**
         * Attribute representing if this coordinator was last in profit or training mode.
         */
        var Bot.profitMode by Attr.boolean { false }

        /**
         * Supported skilling script factories, shared with explicit developer skill selection.
         */
        val factories: Map<Int, SkillingScriptFactory> = listOf(
            MiningScriptFactory,
            ThievingScriptFactory,
            WoodcuttingScriptFactory,
            FiremakingScriptFactory,
            FletchingScriptFactory,
            CraftingScriptFactory,
            HerbloreScriptFactory,
            SmithingScriptFactory,
            CookingScriptFactory,
            FishingScriptFactory,
            MagicScriptFactory,
            RunecraftingScriptFactory,
            PrayerScriptFactory,
            AgilityScriptFactory,
            SlayerScriptFactory,
            FarmingScriptFactory
        ).associateBy { it.skillId }

        /**
         * Keeps training available while any supported skill still advances the account's training goal.
         */
        @JvmStatic
        fun hasTrainingSkills(bot: Bot): Boolean = factories.keys.any { BotCombatActionHandler.wantsToTrain(bot, it) }
    }

    /**
     * Weights only skills with a supported factory, preserving account-build eligibility and preferences.
     * Prayer is available for training; its current methods do not provide a profit activity.
     */
    internal fun skillWeights(bot: Bot): Map<Int, Double> {
        val weights = HashMap<Int, Double>()
        fun eligible(skill: Int) = if (training) BotCombatActionHandler.wantsToTrain(bot, skill)
            else BotCombatActionHandler.mayGainExperience(bot, skill)

        for (skillId in factories.keys) {
            if (!training && skillId == SKILL_PRAYER) continue
            if (!eligible(skillId)) continue
            // In training mode, low level skills are more likely to be selected. In profit mode, the inverse applies.
            val level = bot.skill(skillId).staticLevel
            val levelFactor = if (training) 1.0 - (level / 100.0) else level / 100.0
            weights[skillId] = (levelFactor * LEVEL_WEIGHT) + 0.10
        }

        for (skill in bot.preferences.skills) {
            weights.computeIfPresent(skill) { _, old -> old + PREFERENCE_WEIGHT }
        }

        if (training) weights.computeIfPresent(SKILL_MAGIC) { _, old -> old * MAGIC_TRAINING_WEIGHT_MULTIPLIER }

        return weights
    }

    override fun accept(bot: Bot) {
        val selectedSkill = RandomUtils.weightedRoll(skillWeights(bot)) ?: return
        val factory = factories[selectedSkill] ?: return
        val zones = ArrayList<SubZone>()
        bot.profitMode = !training
        val script = factory.getScript(bot, bot.skill(selectedSkill).staticLevel, zones, training)
        bot.scriptStack.push(script)
    }
}
