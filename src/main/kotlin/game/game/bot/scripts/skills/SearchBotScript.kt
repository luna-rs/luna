package game.bot.scripts.skills

import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.skill.thieving.searchForTraps.ThievingChest
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Handles bots that thieve chests by searching them for traps.
 *
 * This script targets one or more configured [ThievingChest] definitions. The base [SkillingBotScript] handles
 * searching for chest objects, walking to them, and interacting with them. This class adds chest-specific target
 * lookup, survival food logic, personality-based interaction mistakes, and save/restore support.
 *
 * Bots with higher intelligence or dexterity are more likely to use the correct "Search for traps" option. Less
 * intelligent or less dextrous bots can accidentally click the first option, making chest thieving less robotic.
 *
 * @property chests The chest definitions this script is allowed to target.
 *
 * @author lare96
 */
class SearchBotScript(
    bot: Bot,
    val chests: Set<ThievingChest>,
    duration: Duration,
    zones: MutableList<SubZone>
) : SkillingBotScript<GameObject>(bot, duration, zones, bot.thieving) {

    companion object {

        /**
         * Serializable data used to save and restore a [SearchBotScript].
         *
         * This extends [ZonedBotScriptData] with the configured thieving chest set, allowing the script to resume with
         * the same target chests, remaining duration, and candidate zones.
         */
        class SearchData : ZonedBotScriptData() {

            /**
             * The chest definitions this script is allowed to target.
             */
            var chests = emptySet<ThievingChest>()

            override fun load(data: JsonObject) {
                super.load(data)
                chests = loadEnumSet("chests", data) { ThievingChest.valueOf(it) }
            }

            override fun save(data: JsonObject) {
                super.save(data)
                saveEnumSet("chests", data, chests)
            }
        }
    }

    /**
     * Recreates a search-for-traps bot script from saved script data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved search-for-traps script data.
     */
    constructor(bot: Bot, data: SearchData) : this(bot, data.chests, data.duration, data.zones)

    /**
     * The object ids belonging to the configured [chests].
     */
    private val chestIds = run {
        val ids = HashSet<Int>()
        for (chest in chests) {
            ids.add(chest.id)
        }
        ids
    }

    override suspend fun onExecuteSkilling(searching: Boolean) {
        if (bot.emotions.isNervousAboutHp && !handler.inventory.eatAnyFood()) {
            bot.log("No food in inventory, banking for more.")
            forceBanking = true
        }
    }

    override suspend fun onBankOpenSkilling(initial: Boolean) {
        val remainingSpace = bot.inventory.computeRemainingSize()
        if (!handler.banking.withdrawAnyFood((remainingSpace * 0.15).toInt())) {
            bot.log("No food left, ending script.")
            stop()
        }
    }

    override fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        return world.locator.findObjects(searchBase, searchRadius, true) { it.id in chestIds }
    }

    /**
     * Returns the object interaction option this bot should use.
     *
     * Most capable bots use option `2`, which should be the "Search for traps" option. Less intelligent or less
     * dextrous bots can accidentally use option `1`, simulating a misclick or poor game knowledge.
     */
    override fun interactionOption(): Int =
        if (bot.personality.isIntelligent || bot.personality.isDextrous ||
            rand(bot.personality.intelligence) || rand(bot.personality.dexterity)) 2
        else 1

    override fun levelRequired(): Int = chests.maxOfOrNull { it.level } ?: 0

    override fun snapshot(): BotScriptData {
        val data = SearchData()
        data.chests = chests
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }
}