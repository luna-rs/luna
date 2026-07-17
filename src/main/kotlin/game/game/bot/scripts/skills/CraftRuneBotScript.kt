package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.item.Item
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Crafts runes at a specific Runecrafting altar.
 *
 * It withdraws the required essence and talisman, finds the inside altar object, and interacts with it until the
 * inventory runs out of essence.
 *
 * @param bot The bot running this script.
 * @param altar The altar this script crafts runes at.
 * @param duration How long this script should run before completing normally.
 * @author lare96
 */
class CraftRuneBotScript(bot: Bot, val altar: Altar, duration: Duration) : InventoryBotScript(bot, duration, mutableListOf(altar.zone)) {

    companion object {

        /**
         * The item id for normal rune essence.
         */
        const val RUNE_ESSENCE = 1436

        /**
         * The item id for pure essence.
         */
        const val PURE_ESSENCE = 7936

        /**
         * Serializable data for restoring a [CraftRuneBotScript].
         *
         * This stores the selected [altar] along with the normal zone-script duration and zone data.
         */
        class CraftRuneData : ZonedBotScriptData() {

            /**
             * The altar this script should craft runes at.
             */
            var altar = Altar.AIR

            override fun load(data: JsonObject) {
                super.load(data)
                altar = Altar.valueOf(data.get("altar").asString)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("altar", altar.name)
            }
        }
    }

    /**
     * Creates a rune-crafting script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved rune-crafting script data.
     */
    constructor(bot: Bot, data: CraftRuneData) : this(bot, data.altar, data.duration)

    /**
     * The cached inside altar object.
     *
     * This is resolved from [CraftableRune.insideAltarObject] and cleared when the script is paused.
     */
    private var altarObject: GameObject? = null

    /**
     * The rune being crafted by this script.
     *
     * This is resolved from [altar] during banking so execution can use the matching inside altar object.
     */
    private var rune: CraftableRune? = null

    private var essence: Int? = null

    override fun withdraw(): List<Item> {
        rune = CraftableRune.ALTAR_TO_RUNE[altar]
        val craftingRune = rune
        if (craftingRune == null) {
            bot.log("No rune mapping for altar [$altar].")
            stop()
            return listOf()
        }

        if (!bot.itemTracker.contains(RUNE_ESSENCE) && !bot.itemTracker.contains(PURE_ESSENCE)) {
            bot.log("No rune or pure essence.")
            stop()
            return listOf()
        }

        val requiresPureEssence = craftingRune.level > 20
         essence = when {
            requiresPureEssence && bot.itemTracker.count(PURE_ESSENCE) >= 27 -> PURE_ESSENCE
            requiresPureEssence -> null
            bot.itemTracker.count(RUNE_ESSENCE) >= 27 -> RUNE_ESSENCE
            bot.itemTracker.count(PURE_ESSENCE) >= 27 -> PURE_ESSENCE
            else -> null
        }

        if (essence == null) {
            bot.log("No valid essence available for $craftingRune.")
            stop()
            return listOf()
        }

        if (!bot.itemTracker.contains(altar.talisman)) {
            // Emergency spawn talisman if needed.
            bot.bank.add(altar.talisman)
        }

        return listOf(Item(essence!!, 27), Item(altar.talisman))
    }

    override suspend fun onExecuteInZone(): Boolean {
        val zone = activeZone!!
        val craftingRune = rune
        if (craftingRune == null) {
            bot.log("Invalid rune.")
            stop()
            return true
        }
        if(essence == null) {
            bot.log("Invalid essence.")
            stop()
            return true
        }

        if (!bot.inventory.contains(essence!!)) {
            bot.log("No more essence, banking.")
            forceBanking = true
            return true
        }

        if (altarObject == null) {
            altarObject = world.locator
                .findObjects(zone.area.centerPosition, zone.area.tileRadius) { it.id == craftingRune.insideAltarObject }
                .firstOrNull()
        }

        bot.log("Attempting to interact with Runecrafting altar.")
        val altarObj = altarObject
        if (altarObj == null) {
            bot.log("No Runecrafting altar object found in zone area.")
            stop()
            return true
        }

        if (!handler.interactions.interact(1, altarObj)) {
            bot.log("Could not interact with Runecrafting altar. Trying again next cycle.")
            return true
        }

        bot.naturalDecisionDelay()
        return true
    }

    override fun snapshot(): BotScriptData {
        val data = CraftRuneData()
        data.duration = duration
        data.altar = altar
        data.zones = originalZones.toMutableList()
        return data
    }

    override fun onPaused() {
        altarObject = null
    }
}