package game.bot.scripts.skills

import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.stringBow.Bow
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles stationary bot bow-stringing by repeatedly using bow string on one configured unstrung bow type.
 *
 * [StationaryInventoryBotScript] ensures the bot is already in a valid stationary zone where it can safely process
 * inventory actions. Banking is requested when the bot runs out of either unstrung bows or bow strings. The script
 * withdraws fourteen unstrung bows and fourteen bow strings so each inventory can process a full batch of completed bows.
 *
 * [Bow.ARROW_SHAFT] is rejected because arrow shafts are created by cutting logs, not by stringing bows.
 *
 * @property bow The bow type this script should string.
 * @author lare96
 */
class StringBowBotScript(
    bot: Bot,
    val bow: Bow,
    duration: Duration
) : StationaryInventoryBotScript(bot, duration) {

    init {
        require(bow != Bow.ARROW_SHAFT) { "ARROW_SHAFT bow type cannot be used with this script." }
    }

    companion object {

        /**
         * Serializable data used to save and restore a [StringBowBotScript].
         *
         * This extends [ZonedBotScriptData] with the configured [Bow], allowing the script to resume with the same
         * bow type, remaining duration, and candidate zones.
         */
        class StringBowData : ZonedBotScriptData() {

            /**
             * The bow type the bot should string.
             */
            var bow: Bow = Bow.SHORTBOW

            override fun load(data: JsonObject) {
                super.load(data)
                bow = Bow.valueOf(data.get("bow").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("bow", bow.name)
            }
        }
    }

    /**
     * Recreates a bow-stringing bot script from saved script data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved bow-stringing script data.
     */
    constructor(bot: Bot, data: StringBowData) : this(bot, data.bow, data.duration)

    override suspend fun onExecuteInZone(): Boolean {
        if (bow.unstrung !in bot.inventory) {
            bot.log("No unstrung ${bow.name} left in inventory; requesting bank trip.")
            forceBanking = true
            return true
        }

        if (Bow.BOW_STRING !in bot.inventory) {
            bot.log("No bow strings left in inventory; requesting bank trip.")
            forceBanking = true
            return true
        }

        bot.log("Using bow string on unstrung ${bow.name}; waiting for make-item dialogue.")
        handler.inventory.useItem(Bow.BOW_STRING).onItem(bow.unstrung)

        if (!waitFor(1200.milliseconds) { MakeItemDialogue::class in bot.overlays }) {
            bot.log("Make-item dialogue did not open for ${bow.name}; retrying next cycle.")
            return true
        }

        bot.log("Selecting make-item index 0 for ${bow.name}.")
        handler.widgets.clickMakeItem(0, Int.MAX_VALUE)

        bot.naturalDexterityDelay()
        return true
    }

    override fun withdraw(): List<Item> {
        return listOf(Item(bow.unstrung, 14), Item(Bow.BOW_STRING, 14))
    }

    override fun tools(): Set<Int> {
        return emptySet()
    }

    override fun snapshot(): BotScriptData {
        val data = StringBowData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.bow = bow
        return data
    }
}