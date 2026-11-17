package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import io.luna.game.model.mob.bot.Bot
import api.bot.script.InventoryBotScript
import api.bot.action.BotPieMaintenance
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.productionBatch
import engine.bot.speech.BotConversationPool.ConversationSpeech
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.cooking.cookFood.Cooking.COOKING_OBJECTS
import game.skill.cooking.cookFood.CookingInterface
import game.skill.cooking.cookFood.CookFoodActionItem
import game.skill.cooking.cookFood.Food
import io.luna.game.model.EntityState
import io.luna.game.model.item.Equipment.HANDS
import io.luna.game.model.item.Item
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bot script for cooking raw food.
 *
 * The script withdraws raw food from the bank, finds a nearby cooking object, uses the raw food on that object, and
 * starts cooking through the cooking interface.
 *
 * A specific [selectedFood] can be provided when the factory already knows what the bot should cook. If no food is
 * selected, the script searches the bot's bank for the first raw food entry it can cook from [Food.entries].
 * Explicit choices survive save/restoration. Legacy snapshots and automatic selections continue choosing food
 * from available stock rather than acquiring a fixed recipe during restoration.
 *
 * This script supports normal bank-based cooking zones as well as Rogues' Den, which uses a custom cached banker NPC
 * instead of the default bank lookup. The NPC is resolved again for each banking request; if it is absent,
 * the inherited bank lookup and travel fallback can proceed.
 *
 * Verified unnoted withdrawals and the shared interaction retry budget bound failed facilities and menus.
 * Real raw-food consumption confirms progress, including burns. Current level and the selected recipe/source
 * are checked before the ordinary cook-all button. Finishing interrupts only this recipe's cooking action.
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
    override val ambientProductionTopic = ConversationSpeech.COOKING
    override val verifiedProductionWithdrawals = true
    override val trainingSkill = SKILL_COOKING


    companion object {

        /**
         * Saved optional factory-selected food alongside the inherited duration and candidate zones.
         * An absent food field retains the automatic selection used by legacy snapshots.
         *
         * @author lare96
         */
        class CookFoodData : InventoryScriptData() {
            /** Explicit configured recipe, or null when available stock determines the recipe. */
            var food: Food? = null

            override fun load(data: JsonObject) {
                super.load(data)
                food = data.get("food")?.takeUnless { it.isJsonNull }?.asString?.let { Food.valueOf(it) }
            }

            override fun save(data: JsonObject) {
                super.save(data)
                if (food != null) data.addProperty("food", food!!.name) else data.remove("food")
            }
        }



    }

    /**
     * Restores an explicit recipe from new snapshots, or automatic selection from older zoned data.
     * Keeping the zoned-data constructor lets the existing central registration accept both data classes.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) :
        this(bot, (data as? CookFoodData)?.food, data.duration, data.zones) {
        if (data is InventoryScriptData) restoreInventoryState(data)
    }

    /**
     * Cached cooking object for the active zone.
     *
     * Once found, the script reuses this object until it leaves the world or an attempt to cook on it fails.
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

    override fun bankWithdraw(): List<Item> {
        val selected = withdraw().firstOrNull() ?: return emptyList()
        return bot.productionBatch(listOf(Item(selected.id)))
    }

    override fun withdraw(): List<Item> {
        cooking = null
        if (selectedFood == null) {
            for (food in Food.entries) {
                if (bot.cooking.staticLevel >= food.lvl && bot.itemTracker.count(food.raw) > 0) {
                    cooking = food
                    break
                }
            }
        } else {
            if (bot.cooking.staticLevel >= selectedFood.lvl) {
                cooking = selectedFood
            }
        }

        val food = cooking
        if (food != null) {
            val amount = bot.itemTracker.count(food.raw).coerceAtMost(28)
            if (amount > 0) return listOf(Item(food.raw, amount))
        }

        bot.log("No raw food for cooking could be found in the bank.")
        stop()
        return listOf()
    }

    override suspend fun onExecuteInZone(): Boolean {
        val zone = activeZone!!
        val food = cooking
        if (food == null) {
            bot.log("No food for cooking configured.")
            stop()
            return true
        }

        if (!productionReady(bot.inventory.contains(food.raw), bot.cooking.level >= food.lvl)) return true

        // A lit fire burns out and leaves the world, so look again once the cached object is gone.
        if (cookingObject?.state != EntityState.ACTIVE) {
            // Ranges and map fires stay put, so they come before fires that other players have lit.
            cookingObject = world.locator
                .findObjects(zone.area.centerPosition.setZ(zone.inside.z), zone.area.tileRadius) { it.id in COOKING_OBJECTS }
                .minByOrNull { it.isDynamic }
        }

        bot.log("Attempting to interact with cooking object.")

        val cookObj = cookingObject
        fun permitted() = !isPaused() && !isTerminated() && isActionSafe() &&
            cookObj?.state == EntityState.ACTIVE && bot.inventory.contains(food.raw) && bot.cooking.level >= food.lvl
        attemptProduction(food.raw, available = cookObj != null) {
            if (cookObj == null || !handler.inventory.useItem(food.raw).onObject(cookObj, ::permitted)) {
                return@attemptProduction false
            }
            if (!waitFor(3.seconds) { CookingInterface::class in bot.overlays }) return@attemptProduction false
            val menu = bot.overlays[CookingInterface::class]
            if (menu?.food != food || menu.cookObj != cookObj || !permitted()) return@attemptProduction false
            output.clickButton(13717)
        }
        if (failures > 0) cookingObject = null
        return true
    }

    override suspend fun afterProductionSettled() {
        if (cooking?.let { it.raw in bot.inventory } == true ||
            bot.actions.first(CookFoodActionItem::class.java) != null) return
        BotPieMaintenance.recycleCarried(bot) { !isPaused() && !isTerminated() }
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (!requestProductionBank(cooking?.let { it.raw in bot.inventory } == true)) return false
        if (activeZone == SubZone.ROGUES_DEN) {
            cachedBank = world.locator.findViewableNpcs(SubZone.ROGUES_DEN.inside) { it.id == 2271 }.firstOrNull()
        }
        return true
    }

    override fun snapshot(): CookFoodData = CookFoodData().also {
        saveInventoryState(it)
        it.food = selectedFood
    }

    override suspend fun finish() {
        bot.actions.first(CookFoodActionItem::class.java)?.takeIf { it.food == cooking }?.interrupt()
    }

    override suspend fun onNewActiveZone(lastZone: SubZone?) {
        cookingObject = null
    }

    override fun onPaused() {
        cookingObject = null
    }
}
