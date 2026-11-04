package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.BankResourceCoordinator
import engine.bot.coordinator.skill.MiningScriptFactory
import engine.bot.coordinator.skill.SmithingScriptFactory
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import game.skill.smithing.BarType
import game.skill.smithing.Smithing
import io.luna.game.action.ActionType
import io.luna.game.model.item.Equipment.HANDS
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * Smelts available ore combinations into bars at a furnace.
 *
 * If [selectedBar] is supplied, this script only attempts to smelt that bar type. If [selectedBar] is `null`, the
 * script scans the bot's bank from the highest bar tier to the lowest and selects the first [BarType] for which the bot
 * has both the required Smithing level and ore combination.
 *
 * Once a bar type is selected, the script withdraws a balanced inventory of its required ores, travels to a furnace,
 * uses the appropriate ore on the furnace, and repeats until the required ore combination is no longer available.
 *
 * When no smeltable ore combination can be found, the script queues a mining script so the bot can gather additional
 * resources. After this script finishes, capable bots may queue a smithing script to process the bars they produced.
 * Bank-relief sessions instead use only the selected owned ores, reject loot and finish without either followup.
 *
 * @param bot The bot running this script.
 * @param selectedBar The specific bar type to smelt, or `null` to choose from available banked ores.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones containing usable furnaces.
 * @property bankRelief Whether only expendable owned ores may be consumed without followup production.
 * @author lare96
 */
class SmeltOreBotScript(
    bot: Bot,
    val selectedBar: BarType? = null,
    duration: Duration,
    zones: MutableList<SubZone>,
    val bankRelief: Boolean = false
) : InventoryBotScript(bot, duration, zones) {
    override val ownedSuppliesOnly get() = bankRelief
    override val trainingSkill get() = if (bankRelief) SKILL_SMITHING else null
    override val verifiedProductionWithdrawals get() = bankRelief
    override fun allowsLoot(item: io.luna.game.model.item.GroundItem): Boolean = !bankRelief && super.allowsLoot(item)

    companion object {

        /**
         * All available bar types ordered from the highest tier to the lowest.
         *
         * This ordering ensures automatic selection prioritizes the highest-level bar the bot can currently smelt.
         */
        val BAR_TYPES_DESCENDING: List<BarType> = BarType.entries.reversed()

        /**
         * Retains an explicitly selected relief recipe and its owned-only mode across restarts.
         *
         * @author lare96
         */
        class SmeltingData : InventoryScriptData() {
            /**
             * A fixed selected recipe, or null for the ordinary stock-driven selection.
             */
            var bar: BarType? = null
            /**
             * Whether mining, acquisition and followup smithing are forbidden for this relief session.
             */
            var bankRelief = false
            override fun load(data: JsonObject) {
                super.load(data)
                bar = data.get("bar")?.takeUnless { it.isJsonNull }?.asString?.let(BarType::valueOf)
                bankRelief = data.get("bank_relief")?.asBoolean ?: false
            }
            override fun save(data: JsonObject) {
                super.save(data)
                if (bar != null) data.addProperty("bar", bar!!.name) else data.remove("bar")
                if (bankRelief) data.addProperty("bank_relief", true) else data.remove("bank_relief")
            }
        }
    }

    /**
     * Recreates a smelting script from saved zone and duration data. Newer snapshots also restore retry counters
     * and retired work sites; older zone-only snapshots retain their original defaults.
     *
     * New snapshots restore a fixed [selectedBar] and relief mode. Older zone-only and inventory snapshots keep their
     * stock-driven recipe selection and ordinary followup behavior.
     *
     * @param bot The bot running this script.
     * @param data The saved zone and duration data.
     */
    constructor(bot: Bot, data: ZonedBotScriptData) :
        this(bot, (data as? SmeltingData)?.bar, data.duration, data.zones,
            (data as? SmeltingData)?.bankRelief ?: false) {
        if (data is InventoryScriptData) restoreInventoryState(data)
    }

    /**
     * The furnace object currently used by this script.
     *
     * The object is cached after its first successful lookup to avoid repeatedly scanning the active zone.
     */
    private var furnaceObject: GameObject? = null

    /**
     * The bar type selected for the current smelting session.
     *
     * This is resolved during [withdraw] from either [selectedBar] or the available ore combinations in the bot's bank.
     */
    private var smelting: BarType? = null

    override suspend fun equipment(): BotGearLocator? {
        if (bankRelief) return null
        val purpose = if (randBoolean() || bot.personality.isSocial) {
            setOf(BotGearPurpose.SHOW_OFF)
        } else {
            setOf(BotGearPurpose.SKILLING)
        }

        // Goldsmith gauntlets.
        return BotGearSelector.find(bot, purpose)
            .replace(HANDS, 776)
            .buildLocator()
    }

    override fun bankWithdraw(): List<Item> = withdraw()

    override fun withdraw(): List<Item> {
        if (bankRelief && (selectedBar == null || bot.smithing.staticLevel < selectedBar.level ||
                !BankResourceCoordinator.canProcess(bot, SKILL_SMITHING, selectedBar.oreList) || !reliefOutputFits())) {
            stop()
            return emptyList()
        }
        smelting = selectedBar ?: BAR_TYPES_DESCENDING.firstOrNull { bar ->
            bot.smithing.staticLevel >= bar.level && bar.oreList.all { bot.itemTracker.count(it.id) >= it.amount }
        }

        val bar = smelting
        if (bar != null && bot.smithing.staticLevel >= bar.level &&
            bar.oreList.all { bot.itemTracker.count(it.id) >= it.amount }) {
            bot.log("Smelting bar: $bar")

            val totalRequired = bar.oreList.sumOf { it.amount }
            val combinations = minOf(
                bot.inventory.capacity() / totalRequired,
                bar.oreList.minOf { bot.itemTracker.count(it.id) / it.amount }
            )

            return bar.oreList.map { ore ->
                Item(ore.id, combinations * ore.amount)
            }
        }

        bot.log("No smeltable ore combinations could be found in the bank.")
        if (bankRelief) { stop(); return emptyList() }
        bot.scriptStack.pushTail(
            MiningScriptFactory.getScript(
                bot,
                bot.mining.staticLevel,
                mutableListOf(),
                randBoolean()
            ),
            2
        )
        stop()
        return emptyList()
    }

    override suspend fun onNewActiveZone(lastZone: SubZone?) {
        furnaceObject = null
    }

    override suspend fun onExecuteInZone(): Boolean {
        if (bankRelief && (selectedBar == null || bot.smithing.staticLevel < selectedBar.level ||
                !BankResourceCoordinator.canProcess(bot, SKILL_SMITHING, selectedBar.oreList) || !reliefOutputFits())) {
            stop()
            return true
        }
        val zone = activeZone!!
        val bar = smelting

        if (bar == null) {
            bot.log("No smeltable bar configured.")
            stop()
            return true
        }

        if (!bot.inventory.containsAll(bar.oreList) && bot.actions.size(ActionType.WEAK) == 0) {
            forceBanking = true
            return true
        }

        if (furnaceObject?.let { it.position in zone.area } != true) {
            furnaceObject = world.locator
                .findObjects(zone.area.centerPosition, zone.area.tileRadius) {
                    it.position in zone.area && it.id in Smithing.FURNACE_OBJECTS
                }
                .firstOrNull()
        }

        bot.log("Attempting to interact with furnace.")

        val furnace = furnaceObject
        if (furnace == null) {
            bot.log("No furnace object found in zone area.")
            return false
        }

        val useId = if (bar == BarType.STEEL) {
            bar.oreRequired.second!!.id
        } else {
            bar.oreRequired.first.id
        }

        attemptProduction(useId, outputId = bar.id) {
            handler.inventory.useItem(useId).onObject(furnace)
        }
        return true
    }

    override suspend fun onInventoryBankRequested(): Boolean {
        if (bankRelief) return requestProductionBank(smelting?.let { bot.inventory.containsAll(it.oreList) } == true)
        return forceBanking || !(smelting?.let { bot.inventory.containsAll(it.oreList) } == true)
    }

    override fun snapshot(): InventoryScriptData = SmeltingData().also {
        saveInventoryState(it)
        it.bar = selectedBar
        it.bankRelief = bankRelief
    }

    override suspend fun completed() {
        if (bankRelief) return
        // Chance to queue a smithing script.
        if (rand(bot.personality.intelligence) || bot.personality.isDextrous) {
            val script = SmithingScriptFactory.getSmithingScript(bot, bot.smithing.staticLevel)
            if (script != null) {
                bot.scriptStack.pushTail(script, 2)
            }
        }
    }

    /**
     * Rechecks the full possible output before each relief batch rather than relying on its earlier bank snapshot.
     */
    private fun reliefOutputFits(): Boolean {
        val bar = selectedBar ?: return false
        if ((bot.bank.filterNotNull() + bot.inventory.filterNotNull()).any { it.id == bar.id && it.isDynamic }) return false
        fun owned(id: Int) = bot.bank.computeNonDynamicAmountForId(id).toLong() +
            bot.inventory.computeNonDynamicAmountForId(id)
        val operations = bar.oreList.minOf { owned(it.id) / it.amount }
        return owned(bar.id) + operations <= Int.MAX_VALUE
    }
}
