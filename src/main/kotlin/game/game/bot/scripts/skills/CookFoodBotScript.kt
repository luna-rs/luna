package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.naturalDelay
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.cooking.cookFood.Cooking.COOKING_OBJECTS
import game.skill.cooking.cookFood.CookingInterface
import game.skill.cooking.cookFood.Food
import io.luna.game.action.ActionType
import io.luna.game.model.item.Equipment.HANDS
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Bot script for cooking raw food.
 *
 * The script withdraws raw food from the bank, finds a nearby cooking object, uses the raw food on that object, and
 * starts cooking through the cooking interface.
 *
 * A specific [selectedFood] can be provided when the factory already knows what the bot should cook. If no food is
 * selected, the script searches the bot's bank for the first raw food entry it can cook from [Food.entries].
 *
 * This script supports normal bank-based cooking zones as well as Rogues' Den, which uses a custom cached banker NPC
 * instead of the default bank lookup.
 *
 * @author lare96
 */
class CookFoodBotScript(
    bot: Bot,

    /**
     * The food this bot should cook.
     *
     * If `null`, the script will choose the first raw food type found in the bot's bank.
     */
    val selectedFood: Food? = null,

    duration: Duration,
    zones: MutableList<SubZone>
) : InventoryBotScript(bot, duration, zones) {

    companion object {

        /**
         * Cached Rogues' Den banker.
         *
         * Rogues' Den uses an NPC banker instead of a standard bank object, so the script manually assigns this NPC
         * as the cached bank when running in the Rogues' Den zone.
         */
        private val ROGUES_DEN_BANK =
            lazyVal {
                world.locator
                    .findViewableNpcs(SubZone.ROGUES_DEN.inside) { it.id == 2271 }
                    .first()
            }
    }

    /**
     * Restores this script from saved zoned script data.
     *
     * The selected food is not currently stored in the snapshot, so restored scripts will auto-select food from the
     * bank again.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) : this(bot, null, data.duration, data.zones)

    /**
     * Cached cooking object for the active zone.
     *
     * Once found, the script reuses this object instead of searching every cycle.
     */
    private var cookingObject: GameObject? = null

    /**
     * The food currently being cooked by this script.
     *
     * This is assigned during withdrawal, either from [selectedFood] or by scanning the bot's bank for available
     * raw food.
     */
    private var cooking: Food? = null

    override suspend fun equipment(): BotGearLocator {
        val purpose = if (randBoolean() || bot.personality.isSocial) setOf(BotGearPurpose.SHOW_OFF)
        else setOf(BotGearPurpose.SKILLING)
        // Replace with Cooking gauntlets.
        return BotGearSelector.find(bot, purpose).replace(HANDS, 775).buildLocator()
    }

    override fun withdraw(): List<Item> {
        if (selectedFood == null) {
            for (food in Food.entries) {
                if (bot.itemTracker.count(food.raw) >= 28) {
                    cooking = food
                    break
                }
            }
        } else {
            cooking = selectedFood
        }

        val food = cooking
        if (food != null) {
            return listOf(Item(food.raw, 28))
        }

        bot.log("No raw food for cooking could be found in the bank.")
        stop()
        return listOf()
    }

    override suspend fun onExecuteInZone(): Boolean {
        val zone = activeZone!!

        if (cooking == null) {
            bot.log("No food for cooking configured.")
            stop()
            return true
        }

        if (!bot.inventory.containsAll(withdraw) && bot.actions.size(ActionType.WEAK) == 0) {
            forceBanking = true
            return true
        }

        if (cookingObject == null) {
            cookingObject = world.locator
                .findObjects(zone.area.centerPosition, zone.area.tileRadius) { it.id in COOKING_OBJECTS }
                .firstOrNull()
        }

        bot.log("Attempting to interact with cooking object.")

        val cookObj = cookingObject
        if (cookObj == null) {
            bot.log("No cooking object found in zone area.")
            stop()
            return true
        }

        if (!handler.inventory.useItem(cooking!!.raw).onObject(cookObj)) {
            bot.log("Could not interact with cooking object. Trying again next cycle.")
            return true
        }

        bot.naturalDelay()

        if (CookingInterface::class !in bot.overlays) {
            bot.log("Cooking interface was not opened. Trying again next cycle.")
            return true
        }

        output.clickButton(13717)
        bot.naturalDecisionDelay()
        return true
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (activeZone == SubZone.ROGUES_DEN) {
            // Use banker NPC when at rogues' den.
            cachedBank = ROGUES_DEN_BANK.value
        }
        return true
    }

    override fun snapshot(): BotScriptData {
        val data = ZonedBotScriptData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }

    override fun onPaused() {
        cookingObject = null
    }
}