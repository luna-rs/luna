package game.bot.scripts.skills

import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.naturalDexterityDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.NEEDLE_ID
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.THREAD_ID
import game.skill.crafting.armorCrafting.CraftStuddedActionItem
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.armorCrafting.SoftLeatherInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Crafts leather and studded leather armour.
 *
 * This script handles two crafting paths:
 *
 * - Soft/hard leather armour by using a needle on tanned hide and selecting the correct make-item option.
 * - Studded armour by using steel studs on an existing leather body or leather chaps.
 *
 * The script runs as a stationary inventory-processing script, withdrawing the required materials from the bank and
 * repeating the crafting interaction until another bank trip is needed.
 *
 * @param bot The bot running this script.
 * @param armor The armour item this script should craft.
 * @param duration How long this script should run before completing normally.
 * @author lare96
 */
class CraftArmorBotScript(bot: Bot, val armor: HideArmor, duration: Duration) :
    StationaryInventoryBotScript(bot, duration) {

    companion object {

        /**
         * Serializable script data for [CraftArmorBotScript].
         *
         * This stores the selected [HideArmor] target along with the inherited duration and zone data needed to recreate
         * the script after persistence.
         */
        class CraftArmorData : ZonedBotScriptData() {

            /**
             * The armour item this script should craft.
             */
            var armor: HideArmor = HideArmor.LEATHER_GLOVES

            override fun load(data: JsonObject) {
                super.load(data)
                armor = HideArmor.valueOf(data.get("armor").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("armor", armor.name)
            }
        }
    }

    /**
     * Recreates an armour-crafting script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved armour-crafting script data.
     */
    constructor(bot: Bot, data: CraftArmorData) : this(bot, data.armor, data.duration)

    override suspend fun onExecuteInZone(zone: SubZone): Boolean {
        if (armor.hides != null) {
            if (!bot.inventory.containsAll(NEEDLE_ID, THREAD_ID, armor.hides.first.tan)) {
                bot.log("Not all required supplies are in inventory; requesting bank trip.")
                forceBanking = true
                return true
            }

            handler.inventory.useItem(NEEDLE_ID).onItem(armor.hides.first.tan)
            bot.naturalDelay()

            if (SoftLeatherInterface::class in bot.overlays) {
                val button = SoftLeatherInterface.BUTTON_MAP[armor]?.first
                if (button == null) {
                    bot.log("No button found for $armor.")
                    stop()
                    return true
                }
                output.clickButton(button)
            } else {
                if (!waitFor(1200.milliseconds) { MakeItemDialogue::class in bot.overlays }) {
                    bot.log("Make-item dialogue did not open for $armor; retrying next cycle.")
                    return true
                }

                val armorArray = HideArmor.HIDE_TO_ARMOR[armor.hides.first]
                if (armorArray == null) {
                    bot.log("No soft leather interface or make item dialogue for armor $armor.")
                    stop()
                    return true
                }

                val index = armorArray.indexOfFirst { it == armor.id }
                if (index == -1) {
                    bot.log("No matching armor found in armor array? $armor and ${armor.hides.first}.")
                    stop()
                    return true
                }
                handler.widgets.clickMakeItem(index, Int.MAX_VALUE)
            }
        } else {
            val armorNeeded =
                if (armor == HideArmor.STUDDED_CHAPS) HideArmor.LEATHER_CHAPS.id else HideArmor.LEATHER_BODY.id

            if (!bot.inventory.containsAll(CraftStuddedActionItem.STUDS, armorNeeded)) {
                bot.log("Not all required supplies are in inventory; requesting bank trip.")
                forceBanking = true
                return true
            }

            bot.log("Using steel studs on $armor.")
            handler.inventory.useItem(CraftStuddedActionItem.STUDS).onItem(armorNeeded)
            bot.naturalDexterityDelay()
        }
        return true
    }

    override fun withdraw(): List<Item> {
        return if (armor.hides != null) {
            listOf(Item(armor.hides.first.tan, 26))
        } else if (armor == HideArmor.STUDDED_BODY) {
            listOf(Item(HideArmor.LEATHER_BODY.id, 14), Item(CraftStuddedActionItem.STUDS, 14))
        } else if (armor == HideArmor.STUDDED_CHAPS) {
            listOf(Item(HideArmor.LEATHER_CHAPS.id, 14), Item(CraftStuddedActionItem.STUDS, 14))
        } else {
            throw IllegalStateException("Invalid armor type $armor.")
        }
    }

    override fun tools(): Set<Int> {
        return if (armor.hides != null) setOf(NEEDLE_ID, THREAD_ID) else setOf()
    }

    override fun snapshot(): BotScriptData? {
        val data = CraftArmorData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.armor = armor
        return data
    }
}