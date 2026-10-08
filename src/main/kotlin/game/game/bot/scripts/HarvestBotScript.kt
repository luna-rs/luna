package game.bot.scripts

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScriptData
import api.bot.script.TargetingZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.SkillingScriptFactory
import game.bot.scripts.skills.SpinFlaxBotScript
import game.obj.resource.harvestable.CabbageResource
import game.obj.resource.harvestable.FlaxResource
import game.obj.resource.harvestable.HarvestableResource
import game.obj.resource.harvestable.OnionResource
import game.obj.resource.harvestable.PotatoResource
import game.obj.resource.harvestable.WheatResource
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.Position
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

    /**
     * Whether this bot spins each inventory of flax at the Seers' Village wheel before banking it.
     */
    private var spinFlax = false

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (harvestable == Harvestable.FLAX) {
            SpinFlaxBotScript.leaveSeersWheel(bot)
            if (!resumed) {
                spinFlax = bot.crafting.staticLevel >= Textile.BOWSTRING.level && rand(bot.personality.intelligence)
            }
        }
        return true
    }

    override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        if (!initial && spinFlax && activeZone == SubZone.SOUTH_SEERS_VILLAGE_FLAX &&
            SpinFlaxBotScript.FLAX in bot.inventory) {
            spinFlax = SpinFlaxBotScript.spinAtSeers(bot)
        }
        return true
    }

    // Don't start another plant with a full inventory. Its click would cancel the walk to the bank.
    override suspend fun interactionOption(target: GameObject): Int? = if (bot.inventory.isFull) null else 2

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        return world.locator.findObjects(searchBase, searchRadius, true) { harvestable.resource.isResource(it.def()) }
    }

    override fun snapshot(): BotScriptData {
        val data = HarvestData()
        data.harvestable = harvestable
        data.duration = duration
        data.zones = zones
        return data
    }

    override suspend fun completed() {
        // Chance to queue a flax spinning script, unless this bot spins its own flax.
        if (harvestable == Harvestable.FLAX && !spinFlax &&
            (rand(bot.personality.intelligence) || bot.personality.isDextrous)) {
            val script = SpinFlaxBotScript(bot, SkillingScriptFactory.getDuration(bot))
            bot.scriptStack.pushTail(script, 2)
        }
    }
}