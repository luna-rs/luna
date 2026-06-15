package game.bot.scripts.skills

import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.skill.SkillingTool
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.Iterables
import com.google.gson.JsonObject
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.fishing.Tool
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import java.util.*
import kotlin.time.Duration

/**
 * Bot script for Fishing.
 *
 * The script fishes using a specific [selectedTool], searches for matching fishing spots in the active zone, handles
 * bait withdrawal when required, and interacts with the correct fishing option based on the spot definition.
 *
 * Fishing spots are resolved from both first-click and second-click spot maps, allowing the same tool to support NPCs
 * that require different interaction options.
 *
 * @author lare96
 */
class FishBotScript(
    bot: Bot,

    /**
     * The fishing tool this script should use.
     *
     * This determines:
     * - The required Fishing level.
     * - The required tool item.
     * - The required bait, if any.
     * - The valid fishing spot NPC ids.
     * - Whether the spot should be interacted with using first-click or second-click.
     */
    val selectedTool: Tool,

    duration: Duration,
    zones: MutableList<SubZone>
) : SkillingBotScript<Npc>(bot, duration, zones, bot.fishing) {

    companion object {

        /**
         * Maps each fishing tool to the NPC fishing spots that use it as their first-click action.
         */
        val FIRST_CLICK_SPOTS: ImmutableSetMultimap<Tool, Int> =
            ImmutableSetMultimap.builder<Tool, Int>()
                .putAll(Tool.FISHING_ROD, 233, 234, 235, 236)
                .putAll(Tool.FLY_FISHING_ROD, 309, 310, 311, 314, 315, 317, 318)
                .putAll(Tool.LOBSTER_POT, 312, 321)
                .put(Tool.BIG_NET, 313)
                .putAll(Tool.SMALL_NET, 316, 319, 320, 330, 327)
                .putAll(Tool.MONKFISH_NET, 1174, 322)
                .build()

        /**
         * Maps each fishing tool to the NPC fishing spots that use it as their second-click action.
         */
        val SECOND_CLICK_SPOTS: ImmutableSetMultimap<Tool, Int> =
            ImmutableSetMultimap.builder<Tool, Int>()
                .putAll(Tool.FISHING_ROD, 309, 310, 311, 314, 315, 316, 317, 318, 319, 320, 330, 327)
                .putAll(Tool.HARPOON, 312, 321, 322)
                .put(Tool.SHARK_HARPOON, 313)
                .build()

        /**
         * Serializable data for restoring a Fishing script.
         *
         * Extends [ZonedBotScriptData] with the selected fishing [Tool], allowing the script to resume with the same
         * tool, duration, and zones after it has been snapshotted.
         */
        class FishData : ZonedBotScriptData() {

            /**
             * The fishing tool used by this script.
             *
             * Defaults to [Tool.SMALL_NET] when no saved value is available.
             */
            var tool = Tool.SMALL_NET

            override fun load(data: JsonObject) {
                super.load(data)
                tool = Tool.valueOf(data.get("tool").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("tool", tool.name)
            }
        }
    }

    /**
     * Restores a Fishing script from saved script data.
     */
    constructor(bot: Bot, data: FishData) : this(bot, data.tool, data.duration, data.zones)

    /**
     * All NPC ids that can be fished using [selectedTool].
     *
     * This combines both first-click and second-click fishing spots so the
     * script can search for every valid target for the selected tool.
     */
    private val fishingSpotIds = lazyVal {
        val ids = HashSet<Int>()

        for (spotId in FIRST_CLICK_SPOTS[selectedTool] + SECOND_CLICK_SPOTS[selectedTool]) {
            ids += spotId
        }

        ids
    }

    override fun requirements(): Boolean {
        return fishingSpotIds.value.isNotEmpty()
    }

    override suspend fun onBankOpenSkilling(initial: Boolean) {
        val baitId = selectedTool.bait

        if (baitId == null) {
            return
        }

        if (baitId !in bot.itemTracker) {
            bot.log("We do not have the required bait to use this tool.")
            stop()
            bot.preferences.wantedItems += baitId
            return
        }

        handler.banking.withdrawAll(baitId)
    }

    override fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        println("FISHING DEBUG: finding NPCs, possible fishing spots [${Iterables.toString(fishingSpotIds.value)}]")
        return world.locator.findNpcs(searchBase, searchRadius) {
            if (it.def().name.equals("Fishing spot", true))
                println("FISHING DEBUG: potential target ${it.id}/${it.def().name}")
            it.id in fishingSpotIds.value
        }
    }

    override fun tools(): SortedSet<SkillingTool> {
        return sortedSetOf(SkillingTool(selectedTool.id, selectedTool.level))
    }

    override fun interactionOption(target: Npc): Int {
        if (target.id in SECOND_CLICK_SPOTS[selectedTool]) {
            return 2
        }

        return 1
    }

    override fun levelRequired(): Int {
        return selectedTool.level
    }

    override fun snapshot(): BotScriptData {
        val data = FishData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.tool = selectedTool
        return data
    }
}