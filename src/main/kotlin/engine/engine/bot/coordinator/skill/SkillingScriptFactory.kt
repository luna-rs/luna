package engine.bot.coordinator.skill

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScript
import api.bot.script.recentEscape
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.BASE_SKILLING_DURATION_MINUTES
import kotlin.math.floor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Base factory for creating skilling scripts for a specific skill.
 *
 * Each factory is tied to one skill, identified by [skillId], and can create either a training-focused script or a
 * profit-focused script. Subclasses decide which resources, zones, and behavior are appropriate for the bot's
 * level and objective.
 *
 * @property skillId The skill id this factory creates scripts for.
 * @author lare96
 */
abstract class SkillingScriptFactory(val skillId: Int) {

    companion object {

        /**
         * Selects the duration of a skilling session for [bot].
         *
         * The base duration is randomly chosen from [BASE_SKILLING_DURATION_MINUTES]. Dextrous bots may receive a longer
         * session duration, reflecting a stronger tendency to stay focused on skilling before switching activities.
         *
         * @param bot The bot whose personality affects the duration.
         * @return The selected skilling session duration.
         */
        fun getDuration(bot: Bot): Duration {
            var baseDuration = rand(BASE_SKILLING_DURATION_MINUTES)

            if (bot.personality.isDextrous) {
                baseDuration = floor(baseDuration * rand(1.25, 1.75)).toInt()
            }

            return baseDuration.minutes
        }
    }

    // TODO Profit function to determine which element is the most profitable to harvest/process right now.

    /**
     * Creates a training-focused skilling script.
     *
     * Training scripts should prioritize experience gain, progression, and sensible account development over profit.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current level in this factory's skill.
     * @param zones The mutable zone list to populate with valid training areas.
     * @return A skilling script configured for training.
     */
    abstract fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript

    /**
     * Creates a profit-focused skilling script.
     *
     * Profit scripts should prioritize useful or valuable resources, while still respecting the bot's level and
     * available training areas.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current level in this factory's skill.
     * @param zones The mutable zone list to populate with valid profit areas.
     * @return A skilling script configured for profit.
     */
    abstract fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript

    /**
     * Creates either a training or profit script for [bot].
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current level in this factory's skill.
     * @param zones The mutable zone list to populate with valid skilling areas.
     * @param training If `true`, creates a training script. Otherwise, creates a profit script.
     * @return A skilling script matching the requested mode.
     */
    fun getScript(bot: Bot, level: Int, zones: MutableList<SubZone>, training: Boolean): BotScript {
        return if (training) {
            getTrainingScript(bot, level, zones)
        } else {
            getProfitScript(bot, level, zones)
        }
    }

    /**
     * Returns the activities that are available at the supplied level.
     *
     * Each option is checked with [levelFunc]. Options with a required level higher than [level] are removed, and the
     * remaining options are returned sorted from highest requirement to lowest requirement.
     *
     * @param E The activity type being filtered.
     * @param level The current level used to unlock activities.
     * @param levelFunc Returns the level required for an activity.
     * @param options The full collection of possible activities.
     *
     * @return The activities available at [level], sorted by requirement.
     */
    protected fun <E> getActivities(level: Int, levelFunc: (E) -> Int, options: Collection<E>): List<E> {
        val optionsMutable = ArrayList<E>(options)
        optionsMutable.removeIf { levelFunc(it) > level }
        optionsMutable.sortByDescending { levelFunc(it) }
        return optionsMutable
    }

    /**
     * Selects an available activity using the bot's personality.
     *
     * Activities are first filtered by [level] through [getActivities]. Dextrous or intelligent bots prefer the best
     * available activity, dumb bots may pick a worse or random activity, and average bots pick randomly.
     *
     * @param E The activity type being selected.
     * @param bot The bot whose personality influences the selection.
     * @param level The current level used to unlock activities.
     * @param levelFunc Returns the level required for an activity.
     * @param options The full collection of possible activities.
     *
     * @return The selected activity, or `null` if no activity is available.
     */
    protected fun <E> getBestActivity(bot: Bot, level: Int, levelFunc: (E) -> Int, options: Collection<E>): E? {
        val activities = ArrayList<BotSkillingActivity<E>>()
        for (option in options) {
            activities.add(BotSkillingActivity(tokens = setOf(option)))
        }
        val selected = getBestSkillingActivity(bot, level, levelFunc, activities) ?: return null
        return selected.tokens.single()
    }

    /**
     * Removes locked tokens and empty activities without separating resources from their locations.
     */
    protected fun <T> getSkillingActivities(
        level: Int,
        levelFunc: (T) -> Int,
        options: Collection<BotSkillingActivity<T>>
    ): List<BotSkillingActivity<T>> {
        val activities = ArrayList<BotSkillingActivity<T>>()
        for (activity in options) {
            val tokens = linkedSetOf<T>()
            for (token in activity.tokens) {
                if (levelFunc(token) <= level) {
                    tokens.add(token)
                }
            }
            if (tokens.isNotEmpty()) {
                activities.add(activity.copy(tokens = tokens))
            }
        }
        activities.sortByDescending { it.tokens.maxOf(levelFunc) }
        return activities
    }

    /**
     * Applies the existing best/worst/random personality choice to complete resource-and-location groups. Activities
     * whose every zone is near the bot's recent escape from an attacker are left out while others remain, so the bot
     * isn't sent straight back to that attacker.
     */
    protected fun <T> getBestSkillingActivity(
        bot: Bot,
        level: Int,
        levelFunc: (T) -> Int,
        options: Collection<BotSkillingActivity<T>>
    ): BotSkillingActivity<T>? {
        val available = getSkillingActivities(level, levelFunc, options)
        val escape = bot.recentEscape
        val activities = if (escape == null) available else
            available.filterNot { activity -> activity.zones.isNotEmpty() && activity.zones.all { escape.avoids(it) } }
                .ifEmpty { available }
        if (bot.personality.isDextrous || bot.personality.isIntelligent) {
            return activities.firstOrNull()
        } else if (bot.personality.isDumb) {
            return if (randBoolean()) activities.lastOrNull() else activities.randomOrNull()
        } else {
            return activities.randomOrNull()
        }
    }
}
