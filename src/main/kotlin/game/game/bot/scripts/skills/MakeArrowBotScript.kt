package game.bot.scripts.skills

import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.fletching.attachArrow.Arrow
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Handles stationary bot arrow-making by combining the two materials defined by an [Arrow].
 *
 * This supports both stages of arrow production. [Arrow.HEADLESS_ARROW] attaches feathers to arrow shafts, while the
 * remaining arrow types attach metal arrowtips to headless arrows.
 *
 * @property arrow The arrow recipe this script should produce.
 * @author lare96
 */
class MakeArrowBotScript(
    bot: Bot,
    val arrow: Arrow,
    duration: Duration
) : StationaryInventoryBotScript(bot, duration) {

    companion object {

        /**
         * The number of each arrow-making material withdrawn per banking cycle.
         */
        const val WITHDRAW_AMOUNT = 1000

        /**
         * Serializable data used to save and restore a [MakeArrowBotScript].
         */
        class MakeArrowData : ZonedBotScriptData() {

            /**
             * The arrow recipe the bot should produce.
             */
            var arrow: Arrow = Arrow.HEADLESS_ARROW

            override fun load(data: JsonObject) {
                super.load(data)
                arrow = Arrow.valueOf(data.get("arrow").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("arrow", arrow.name)
            }
        }
    }

    /**
     * Recreates an arrow-making bot script from saved script data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved arrow-making script data.
     */
    constructor(bot: Bot, data: MakeArrowData) : this(bot, data.arrow, data.duration)

    override suspend fun onExecuteInZone(): Boolean {
        if (arrow.tip !in bot.inventory) {
            bot.log("No ${itemName(arrow.tip)} left in inventory; requesting bank trip.")
            forceBanking = true
            return true
        }

        if (arrow.with !in bot.inventory) {
            bot.log("No ${itemName(arrow.with)} left in inventory; requesting bank trip.")
            forceBanking = true
            return true
        }

        val usedId: Int
        val targetId: Int

        if (arrow == Arrow.HEADLESS_ARROW) {
            // Headless arrows are registered as arrow shaft -> feather.
            usedId = arrow.tip
            targetId = arrow.with
        } else {
            // Finished arrows are registered as headless arrow -> arrowtips.
            usedId = arrow.with
            targetId = arrow.tip
        }

        bot.log("Using ${itemName(usedId)} on ${itemName(targetId)}.")
        handler.inventory.useItem(usedId).onItem(targetId)

        if (!waitFor(1200.milliseconds) { MakeItemDialogue::class in bot.overlays }) {
            bot.log("Make-item dialogue did not open for ${arrow.name}; retrying next cycle.")
            return true
        }

        bot.log("Selecting make-item index 0 for ${arrow.name}.")
        handler.widgets.clickMakeItem(0, Int.MAX_VALUE)

        bot.naturalDexterityDelay()
        return true
    }

    override fun withdraw(): List<Item> {
        return listOf(
            Item(arrow.tip, WITHDRAW_AMOUNT),
            Item(arrow.with, WITHDRAW_AMOUNT)
        )
    }

    override fun tools(): Set<Int> {
        return emptySet()
    }

    override fun snapshot(): BotScriptData {
        val data = MakeArrowData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.arrow = arrow
        return data
    }
}