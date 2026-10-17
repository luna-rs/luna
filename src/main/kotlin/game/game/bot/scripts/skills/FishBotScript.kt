package game.bot.scripts.skills

import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.skill.SkillingTool
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableSetMultimap
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CookingScriptFactory
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.catchFish.Tool
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
         * How many times a bot clicks its fishing spot without starting to fish before it looks for another spot.
         */
        private const val SPOT_CLICK_ATTEMPTS = 3

        /**
         * Maps each fishing tool to the NPC fishing spots that use it as their first-click action.
         */
        val FIRST_CLICK_SPOTS: ImmutableSetMultimap<Tool, Int> = buildSpots { it.firstOption }

        /**
         * Maps each fishing tool to the NPC fishing spots that use it as their second-click action.
         */
        val SECOND_CLICK_SPOTS: ImmutableSetMultimap<Tool, Int> = buildSpots { it.secondOption }

        /**
         * Maps each tool returned by [option] to the NPC fishing spots that use it. Bots don't fish in Miscellania,
         * where the catch goes to Fisherman Frodi.
         */
        private fun buildSpots(option: (FishingSpot) -> Tool?): ImmutableSetMultimap<Tool, Int> {
            val spots = ImmutableSetMultimap.builder<Tool, Int>()
            for (spot in FishingSpot.entries) {
                val tool = option(spot)
                if (tool != null && spot != FishingSpot.MISCELLANIA) {
                    spots.putAll(tool, spot.npcs)
                }
            }
            return spots.build()
        }

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
     * This combines both first-click and second-click fishing spots so the script can search for every valid
     * target for the selected tool.
     */
    private val fishingSpotIds = lazyVal {
        val ids = HashSet<Int>()

        for (spotId in FIRST_CLICK_SPOTS[selectedTool] + SECOND_CLICK_SPOTS[selectedTool]) {
            ids += spotId
        }

        ids
    }

    /**
     * The fishing spot that [spotClicks] counts clicks for.
     */
    private var clickedSpot: Npc? = null

    /**
     * How many times in a row the bot has clicked [clickedSpot] without starting to fish.
     */
    private var spotClicks = 0

    override fun requirements(): Boolean {
        return fishingSpotIds.value.isNotEmpty()
    }

    override suspend fun onBankOpenSkilling(initial: Boolean) {
        val baitId = selectedTool.bait ?: return

        if (baitId !in bot.itemTracker) {
            bot.log("We do not have the required bait to use this tool.")
            stop()
            bot.preferences.addWantedItem(baitId, 100_000)
            return
        }

        // TODO Only withdraw percentage amount based on intelligence.
        handler.banking.withdrawAll(baitId)
    }

    override suspend fun onExecuteSkilling(searching: Boolean) {
       val bait = selectedTool.bait
        if(bait != null && bait !in bot.inventory) {
            forceBanking = true
        }
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        return world.locator.findNpcs(searchBase, searchRadius) {
            it.id in fishingSpotIds.value
        }
    }

    override val targetSearchTimeout: Duration = 180.seconds

    override fun tools(): SortedSet<SkillingTool> {
        return sortedSetOf(SkillingTool(selectedTool.id, selectedTool.level))
    }

    override fun emergencyTool(): SkillingTool {
        return SkillingTool(selectedTool.id, selectedTool.level)
    }

    override suspend fun interactionOption(target: Npc): Int {
        if (target.id in SECOND_CLICK_SPOTS[selectedTool]) {
            return 2
        }

        return 1
    }

    override fun levelRequired(): Int {
        return selectedTool.level
    }

    override suspend fun refocus(): Boolean {
        val spot = focus
        if (spot !== clickedSpot || bot.actions.size(ActionType.WEAK) > 0) {
            clickedSpot = spot
            spotClicks = 0
        } else if (spot?.state == EntityState.ACTIVE && ++spotClicks > SPOT_CLICK_ATTEMPTS) {
            // TODO Remove once the web walker (#617) handles navigation. Walking to an NPC only tries the side of it
            //  nearest the bot, which is often water once a fishing spot has moved, so the bot would keep clicking a
            //  spot it can't reach until the spot moved again. Drop it and search for another spot instead.
            bot.log("Couldn't start fishing at ${spot.position}, finding another spot.")
            spotClicks = 0
            return true
        }
        return super.refocus()
    }

    override fun snapshot(): BotScriptData {
        val data = FishData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.tool = selectedTool
        return data
    }

    override suspend fun completed() {
        // Chance to queue a fletching script.
        if (rand(bot.personality.intelligence) || bot.personality.isDextrous) {
            val script = CookingScriptFactory.getScript(bot, bot.cooking.staticLevel, mutableListOf(), randBoolean())
            bot.scriptStack.pushTail(script, 2)
        }
    }
}
