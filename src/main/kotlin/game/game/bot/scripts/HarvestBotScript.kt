package game.bot.scripts

import api.bot.script.BotScriptData
import api.bot.script.TargetingZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.obj.resource.harvestable.CabbageResource
import game.obj.resource.harvestable.FlaxResource
import game.obj.resource.harvestable.HarvestableResource
import game.obj.resource.harvestable.OnionResource
import game.obj.resource.harvestable.PotatoResource
import game.obj.resource.harvestable.WheatResource
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * A generic object-targeting script for harvesting simple world resources.
 *
 * This script is used for low-complexity resources that can be harvested directly from world objects, such as cabbages,
 * flax, onions, potatoes, and wheat. It searches for matching objects inside the active [SubZone], filters them through
 * the selected [HarvestableResource], and lets [TargetingZonedBotScript] handle target selection, interaction retries,
 * focus caching, and zone switching.
 *
 * @param bot The bot running this script.
 * @param harvestable The resource type this script should harvest.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones this script may harvest in.
 * @author lare96
 */
class HarvestBotScript(
    bot: Bot,
    val harvestable: Harvestable,
    duration: Duration,
    zones: MutableList<SubZone>
) : TargetingZonedBotScript<GameObject>(bot, duration, zones) {

    companion object {

        /**
         * A simple enum wrapper around harvestable world resource definitions.
         *
         * The enum gives script data a stable, serializable value while each [HarvestableResource] implementation handles
         * the actual object-definition matching and resource-specific behavior.
         *
         * @property resource The resource definition used to identify valid harvest objects.
         */
        enum class Harvestable(val resource: HarvestableResource) {

            /**
             * Harvests cabbage field objects.
             */
            CABBAGE(CabbageResource),

            /**
             * Harvests flax field objects.
             */
            FLAX(FlaxResource),

            /**
             * Harvests onion field objects.
             */
            ONION(OnionResource),

            /**
             * Harvests potato field objects.
             */
            POTATO(PotatoResource),

            /**
             * Harvests wheat field objects.
             */
            WHEAT(WheatResource)
        }

        /**
         * Serializable script data for [HarvestBotScript].
         *
         * This stores the selected [Harvestable] resource along with the inherited zone and duration data needed to
         * recreate the script after persistence.
         */
        class HarvestData : ZonedBotScriptData() {

            /**
             * The resource type this script should harvest.
             */
            var harvestable: Harvestable? = null

            override fun load(data: JsonObject) {
                super.load(data)
                harvestable = Harvestable.valueOf(data.get("resource").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("resource", harvestable!!.name)
            }
        }
    }

    /**
     * Recreates a harvest script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved harvest script data.
     */
    constructor(bot: Bot, data: HarvestData) : this(bot, data.harvestable!!, data.duration, data.zones)

    override fun interactionOption(): Int = 2

    override fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        return world.locator.findObjects(searchBase, searchRadius, true) { harvestable.resource.isResource(it.def()) }
    }

    override fun snapshot(): BotScriptData {
        val data = HarvestData()
        data.harvestable = harvestable
        data.duration = duration
        data.zones = zones
        return data
    }
}