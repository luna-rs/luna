package game.bot.scripts.skills

import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.skill.SkillingTool
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.skill.mining.Ore
import game.skill.mining.Pickaxe
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import java.util.*
import kotlin.time.Duration

/**
 * A [SkillingBotScript] that trains Mining by mining configured ore types inside selected zones.
 *
 * This script supplies the Mining-specific pieces required by [SkillingBotScript], including valid rock object ids,
 * pickaxe selection, emergency pickaxe fallback, level requirements, and serialization data.
 *
 * While running, the bot searches for nearby matching rock objects and interacts with them through the normal skilling
 * script targeting flow.
 *
 * @param bot The bot running this script.
 * @param ores The ore types this bot is allowed to mine.
 * @param duration How long this script should continue running before completing normally.
 * @param zones The candidate zones this script may operate in.
 *
 * @author lare96
 */
class MineBotScript(bot: Bot, val ores: Set<Ore>, duration: Duration, zones: MutableList<SubZone>) :
    SkillingBotScript<GameObject>(bot, duration, zones, bot.mining) {

    companion object {

        /**
         * Serializable data used to save and restore a [MineBotScript].
         *
         * This extends [ZonedBotScriptData] with the configured Mining ore set, allowing the script to resume with the
         * same target ores, remaining duration, and candidate zones.
         */
        class MineData : ZonedBotScriptData() {

            /**
             * The ore types this script is allowed to mine.
             */
            var ores = emptySet<Ore>()

            override fun load(data: JsonObject) {
                super.load(data)
                ores = loadEnumSet("ores", data) { Ore.valueOf(it) }
            }

            override fun save(data: JsonObject) {
                super.save(data)
                saveEnumSet("ores", data, ores)
            }
        }
    }

    /**
     * Creates a [MineBotScript] from persisted script data.
     *
     * @param bot The bot running this script.
     * @param data The saved Mining script data to restore from.
     */
    constructor(bot: Bot, data: MineData) : this(bot, data.ores, data.duration, data.zones)

    /**
     * The rock object ids that match the configured [ores].
     *
     * These ids are precomputed once so [find] can quickly test nearby objects without rebuilding the target id set on
     * every search cycle.
     */
    private var rockObjectIds: Set<Int> = run {
        val ids = HashSet<Int>()
        for (ore in ores) {
            ids.addAll(Ore.ORE_MAP[ore])
        }
        ids
    }

    override fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        return world.locator.findObjects(searchBase, searchRadius) { it.id in rockObjectIds }
    }

    override fun tools(): TreeSet<SkillingTool> =
        Pickaxe.ID_TO_PICKAXE.values.filter { skill.staticLevel >= it.level }
            .mapTo(TreeSet<SkillingTool>()) { SkillingTool(it.id, it.level) }

    override fun emergencyTool(): SkillingTool = SkillingTool(Pickaxe.BRONZE.id, Pickaxe.BRONZE.level)

    override fun levelRequired(): Int = ores.maxOfOrNull { it.level } ?: 0

    override fun snapshot(): BotScriptData {
        val data = MineData()
        data.ores = ores
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }
}