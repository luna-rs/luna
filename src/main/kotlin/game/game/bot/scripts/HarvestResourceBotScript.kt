package game.bot.scripts

import api.bot.skill.HarvestingBotScript
import api.bot.skill.SkillingGoal
import api.bot.skill.SkillingTool
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.HarvestResourceBotScript.Companion.HarvestResourceGoal
import game.obj.resource.harvestable.CabbageResource
import game.obj.resource.harvestable.FlaxResource
import game.obj.resource.harvestable.HarvestableResource
import game.obj.resource.harvestable.OnionResource
import game.obj.resource.harvestable.PotatoResource
import game.obj.resource.harvestable.WheatResource
import io.luna.game.model.Entity
import io.luna.game.model.mob.bot.Bot
import java.util.*
import kotlin.time.Duration

/**
 * A generic resource-harvesting bot script for simple world resources.
 *
 * This script is intended for resources that do not require a dedicated skilling tool or skill reference, such as
 * cabbages, flax, onions, potatoes, and wheat. The bot searches nearby visible objects, filters them through the
 * selected [HarvestableResource], and delegates the harvesting behavior to [HarvestingBotScript].
 *
 * @param bot The bot running this harvesting script.
 * @param goal The resource, zones, and runtime goal used by this script.
 * @author lare96
 */
class HarvestResourceBotScript(bot: Bot, goal: HarvestResourceGoal) :
    HarvestingBotScript<HarvestResourceGoal>(bot, goal, null) {

    //todo flax doesnt work
    /**
     * Shared definitions used by [HarvestResourceBotScript].
     */
    companion object {

        /**
         * The empty tool set used by harvestable world resources.
         *
         * These resources are picked or gathered directly, so the script does not need to reserve, withdraw, equip, or
         * replace a tool before harvesting.
         */
        val DEFAULT_TOOL_SET = TreeSet<SkillingTool>()

        /**
         * The resource types this script can harvest.
         *
         * Each enum value wraps a [HarvestableResource], allowing script goals to work with a small stable enum while the
         * actual resource implementation handles object matching and harvesting behavior.
         *
         * @property resource The resource definition used to identify valid world objects.
         */
        enum class Harvestable(val resource: HarvestableResource) {

            /**
             * Cabbage field resource.
             */
            CABBAGE(CabbageResource),

            /**
             * Flax field resource.
             */
            FLAX(FlaxResource),

            /**
             * Onion field resource.
             */
            ONION(OnionResource),

            /**
             * Potato field resource.
             */
            POTATO(PotatoResource),

            /**
             * Wheat field resource.
             */
            WHEAT(WheatResource)
        }

        /**
         * A goal describing which simple resource the bot should harvest.
         *
         * @param type The resource type this goal targets.
         * @param zones The candidate subzones the bot can use while harvesting.
         * @param duration The amount of time the bot should keep this goal active.
         */
        class HarvestResourceGoal(val type: Harvestable, zones: LinkedList<SubZone>, duration: Duration) : SkillingGoal(zones, duration)
    }

    override fun find(): Collection<Entity> {
        return world.locator.findViewableObjects(bot, true) {
            goal.type.resource.isResource(it.def())
        }
    }

    override fun tools(): SortedSet<SkillingTool> = DEFAULT_TOOL_SET
    override fun levelRequired(): Int = 1
    override fun emergencyTool(): SkillingTool? {
        return null
    }
}