package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.hideTanning.TanInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Tans hides by interacting with a tanner NPC.
 *
 * This script searches the bot's bank for an untanned hide, withdraws enough coins and hides for one inventory cycle,
 * travels to a tanner, opens the tanning interface, and clicks the matching "tan all" button for the selected hide.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @author lare96
 */
class TanHideBotScript(bot: Bot, duration: Duration) :
    InventoryBotScript(bot, duration, mutableListOf(SubZone.AL_KHARID_BANK)) {

    /**
     * Recreates a hide-tanning script from saved zone and duration data.
     *
     * @param bot The bot that owns this script.
     * @param data The previously saved script data.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, data.duration)

    /**
     * The hide type selected for the current tanning cycle.
     *
     * This is resolved from the bot's bank in [withdraw] and reused by the execution loop to choose the correct tanning
     * interface button.
     */
    private var tanning: Hide? = null

    /**
     * The cached tanner NPC used by this script.
     *
     * This avoids scanning the zone every cycle once a tanner has been found.
     */
    private var tannerNpc: Npc? = null

    override suspend fun onInventoryBankRequested(): Boolean {
        val tanning = tanning
        if (tanning == null) {
            return false
        }
        return tanning.hide !in bot.inventory
    }

    override suspend fun onExecuteInZone(zone: SubZone): Boolean {
        if (tannerNpc == null) {
            tannerNpc = world.locator
                .findNpcs(zone.area.centerPosition, zone.area.tileRadius) {
                    it.id == 804 || it.id == 2824 || it.id == 1041
                }
                .firstOrNull()
        }

        bot.log("Attempting to interact with tanner NPC.")
        val tanner = tannerNpc
        if (tanner == null) {
            bot.log("No tanner NPC found in zone area.")
            tannerNpc = null
            stop()
            return false
        }

        if (!handler.interactions.interact(2, tanner)) {
            bot.log("Could not interact with tanner NPC. Trying again next cycle.")
            return true
        }

        bot.log("Waiting for tanning interface to open.")
        if (!waitFor { TanInterface::class in bot.overlays }) {
            bot.log("Tanning interface was not opened. Trying again next cycle.")
            return true
        }

        val button = TanInterface.hideToMakeAll[tanning]
        if (button != null) {
            bot.log("Clicking make all button [$button] for $tanning.")
            output.clickButton(button)
            bot.naturalDecisionDelay()
        } else {
            bot.log("No make all button found for $tanning, ending script.")
            stop()
        }
        return true
    }

    override fun withdraw(): List<Item> {
        for (item in bot.bank) {
            if (item == null) {
                continue
            }
            val hide = Hide.HIDE_TO_HIDE[item.id]
            if (hide != null) {
                tanning = hide
                return listOf(Item(995, hide.cost * 27), Item(hide.hide, 27))
            }
        }

        stop()
        Hide.HIDE_TO_HIDE.keys.forEach { bot.preferences.wantedItems += it }
        bot.log("No untanned hides in bank, ending script.")
        return listOf()
    }

    override fun snapshot(): BotScriptData? {
        val data = ZonedBotScriptData()
        data.duration = duration
        data.zones = zones
        return data
    }
}