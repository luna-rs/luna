package game.bot.scripts.skills

import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.cutLog.Log
import game.skill.fletching.cutLog.Log.Companion.KNIFE
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles stationary bot fletching by repeatedly using a knife on one configured log type.
 *
 * [StationaryInventoryBotScript] ensures the bot is already in a valid stationary zone where it can safely process
 * inventory actions. Banking is requested when the bot runs out of the configured [log]. The script withdraws 27 logs
 * because the knife occupies one inventory slot.
 *
 * @property log The log type this script should fletch.
 * @property index The make-item dialogue index to select after using the knife on the log.
 * @author lare96
 */
class CutLogBotScript(bot: Bot,
                      val log: Log,
                      val index: Int,
                      duration: Duration) : StationaryInventoryBotScript(bot, duration) {

    init {
        require(index >= 0 && index <= 4) { "Make-item index must be within inclusive bounds (0..4)." }
    }

    companion object {

        /**
         * Serializable data used to save and restore a [CutLogBotScript].
         *
         * This extends [ZonedBotScriptData] with the configured fletching [Log] and make-item dialogue index, allowing
         * the script to resume with the same selected product, remaining duration, and candidate zones.
         */
        class CutLogData : ZonedBotScriptData() {

            /**
             * The log type the bot should fletch.
             */
            var log: Log = Log.NORMAL

            /**
             * The make-item dialogue index to select after opening the fletching interface.
             */
            var index = 0

            override fun load(data: JsonObject) {
                super.load(data)
                log = Log.valueOf(data.get("log").asString)
                index = data.get("index").asInt
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("log", log.name)
                data.addProperty("index", index)
            }
        }
    }

    /**
     * Recreates a fletching bot script from saved script data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved fletching script data.
     */
    constructor(bot: Bot, data: CutLogData) : this(bot, data.log, data.index, data.duration)

    override suspend fun onExecuteInZone(): Boolean {
        if (log.id !in bot.inventory) {
            bot.log("No ${log.name} logs left in inventory; requesting bank trip.")
            forceBanking = true
            return true
        }

        if (KNIFE !in bot.inventory) {
            bot.log("Knife is missing from inventory; requesting bank trip to restore tools.")
            forceBanking = true
            return true
        }

        handler.inventory.useItem(KNIFE).onItem(log.id)

        if (!waitFor(1200.milliseconds) { MakeItemDialogue::class in bot.overlays }) {
            bot.log("Make-item dialogue did not open for ${log.name}; retrying next cycle.")
            return true
        }

        bot.log("Selecting make-item index $index for ${log.name}.")
        handler.widgets.clickMakeItem(index, Int.MAX_VALUE)

        bot.naturalDexterityDelay()
        return true
    }

    override fun withdraw(): List<Item> {
        return listOf(Item(log.id, 27))
    }

    override fun tools(): Set<Int> {
        return setOf(KNIFE)
    }

    override fun snapshot(): BotScriptData {
        val data = CutLogData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.log = log
        data.index = index
        return data
    }
}