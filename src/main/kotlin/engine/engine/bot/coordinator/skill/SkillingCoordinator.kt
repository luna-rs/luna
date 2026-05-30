package engine.bot.coordinator.skill

import api.bot.zone.SubZone
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
     * TODO Bot money-making activities, smelting, smithing, etc.
     *
     * Add richer money-making behavior once the economy system, item valuation, and item tagging are stable enough for
     * bots to make sensible profit-based decisions.
     *
     * Fishing:
     * - Fish lobsters, swordfish, and sharks.
     * - Pick fishing spots based on level, confidence, danger, and bank distance.
     *
     * Simple gathering and processing:
     * - Pick flax.
     * - Spin flax into bow strings.
     * - Tan hides.
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
    }

    /**
     * Supported skilling script factories, keyed by skill id.
     */
    private val factories: Map<Int, SkillingScriptFactory> = listOf(
        MiningScriptFactory,
        ThievingScriptFactory,
        WoodcuttingScriptFactory,
        FletchingScriptFactory,
        CraftingScriptFactory
    ).associateBy { it.skillId }

    /**
     * Selects and starts a skilling script for [bot].
     *
     * The selected skill is chosen using weighted odds. If the selected skill does not have a supported factory, a
     * random supported factory is used as a fallback.
     *
     * @param bot The bot that will perform the skilling activity.
     */
    override fun accept(bot: Bot) {
        val weights = HashMap<Int, Double>()

        for (skill in bot.skills) {
            // In training mode, low level skills are more likely to be selected. In profit mode, the inverse applies.
            val levelFactor = if (training) 1.0 - (skill.staticLevel / 100.0) else skill.staticLevel / 100.0
            weights[skill.id] = (levelFactor * LEVEL_WEIGHT) + 0.10
        }

        for (skill in bot.preferences.skills) {
            weights.computeIfPresent(skill) { _, old -> old + PREFERENCE_WEIGHT }
        }

        val selectedSkill = RandomUtils.weightedRoll(weights)
        val level = bot.skill(selectedSkill).staticLevel
        val zones = ArrayList<SubZone>()

        bot.scriptStack.push(run {
            var script = factories[selectedSkill]?.getScript(bot, level, zones, training)

            if (script == null) {
                script = factories.values.random().getScript(bot, level, zones, training)
            }

            script
        })
    }
}