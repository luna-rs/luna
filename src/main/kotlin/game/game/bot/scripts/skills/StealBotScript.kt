package game.bot.scripts.skills

import api.bot.skill.HarvestingBotScript
import api.bot.skill.SkillingGoal
import api.bot.skill.SkillingTool
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.StealBotScript.Companion.StealFromStallGoal
import game.skill.thieving.stealFromStall.StealFromAction
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.Entity
import io.luna.game.model.mob.bot.Bot
import java.util.*
import kotlin.time.Duration

/**
 * A [HarvestingBotScript] thieving script that steals from stalls.
 *
 * The script searches for configured full-stall object ids in the bot's viewable area. Since stealing from stalls
 * can alert nearby guards, smarter low-combat bots may refuse to steal while an attackable guard is watching them.
 *
 * @author lare96
 */
class StealBotScript(bot: Bot, goal: StealFromStallGoal) :
    HarvestingBotScript<StealFromStallGoal>(bot, goal, bot.thieving) {

    companion object {

        /**
         * Shared empty tool set used because stall stealing has no required or emergency skilling tools.
         */
        private val EMPTY_SORTED_SET = TreeSet<SkillingTool>()

        /**
         * Sorts stall types from highest priority to lowest priority.
         *
         * Higher-level stalls are preferred first, with experience used as a secondary priority when two stalls have the
         * same level requirement.
         */
        private val THIEVING_STALL_COMPARATOR = compareBy<ThievingStallType> { it.level }.thenBy { it.xp }.reversed()

        /**
         * The [SkillingGoal] for a [StealBotScript].
         *
         * @param stallTypes The stall types this bot is allowed to steal from.
         * @param zones The candidate zones where this bot may look for stalls.
         * @param duration The amount of time this bot should spend on this goal.
         */
        class StealFromStallGoal(stallTypes: Set<ThievingStallType>, zones: MutableList<SubZone>, duration: Duration)
            : SkillingGoal(zones, duration) {

            /**
             * The configured stall types sorted from highest priority to lowest priority.
             */
            val stallTypes = stallTypes.sortedWith(THIEVING_STALL_COMPARATOR)
        }
    }

    /**
     * Creates a [StealBotScript] from raw stall type, zone, and duration values.
     *
     * @param bot The bot that will steal from stalls.
     * @param stallTypes The stall types this bot is allowed to steal from.
     * @param zones The candidate zones where this bot may look for stalls.
     * @param duration The amount of time this bot should spend stealing from stalls.
     */
    constructor(bot: Bot,
                stallTypes: Set<ThievingStallType>,
                zones: MutableList<SubZone>,
                duration: Duration
    ) : this(bot, StealFromStallGoal(stallTypes, zones, duration))

    /**
     * The full-stall object ids this script can target.
     */
    private val stallIds = run {
        val ids = HashSet<Int>()

        for (type in goal.stallTypes) {
            for ((full, _) in type.stalls) {
                ids.add(full)
            }
        }

        ids
    }

    /**
     * Finds nearby full stalls that are safe enough for the bot to steal from.
     *
     * Smarter low-combat bots avoid stealing while an attackable guard is watching them. This prevents weak bots from
     * repeatedly starting combat with guards when they are likely to lose or waste time. Less intelligent bots, stronger
     * bots, or bots that are not currently being watched will still attempt to steal.
     *
     * @return The nearby full-stall objects this script can attempt to steal from, or an empty collection if the bot
     * should avoid stealing right now.
     */
    override fun find(): Collection<Entity> {
        val isGuardWatching = world.locator.findViewableNpcs(bot) {
            it.combat.isAttackable &&
                    StealFromAction.GUARD_NAMES.contains(it.def().name) &&
                    !it.combat.inCombat() &&
                    it.inViewCone(bot)
        }.isNotEmpty()

        if (bot.personality.intelligence > 0.65 && isGuardWatching && bot.combatLevel < 30) {
            return emptyList()
        }

        return world.locator.findViewableObjects(bot, true) { it.id in stallIds }
    }

    override fun tools(): SortedSet<SkillingTool> = EMPTY_SORTED_SET
    override fun emergencyTool(): SkillingTool? = null
    override fun levelRequired(): Int = goal.stallTypes.first().level
}