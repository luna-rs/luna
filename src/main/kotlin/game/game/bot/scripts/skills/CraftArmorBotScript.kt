package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.crafting.armorCrafting.CraftArmorActionItem
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.NEEDLE_ID
import game.skill.crafting.armorCrafting.CraftArmorActionItem.Companion.THREAD_ID
import game.skill.crafting.armorCrafting.CraftStuddedActionItem
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.armorCrafting.SoftLeatherInterface
import game.skill.crafting.hideTanning.Hide
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Crafts existing leather, snakeskin, dragonhide, and studded armour through the player interactions.
 * One complete recipe across inventory and bank qualifies, including the last partial stock of hides.
 * Leather recipes require a needle and consumable thread; studded recipes require matching leather armour
 * and steel studs. Banking balances inputs to capacity and reserves space for the leather tools.
 *
 * The shared inventory lifecycle handles travel, verified withdrawals, safety, session expiry, and bounded
 * production/banking retries. Snapshots preserve the recipe, zones, duration, and retry counters.
 *
 * @param bot The bot crafting the armour.
 * @property armor The existing armour recipe to produce.
 * @param duration The production session duration.
 * @param zones Existing processing zones with banking and travel support.
 * @author lare96
 */
class CraftArmorBotScript(
    bot: Bot,
    val armor: HideArmor,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Saved armour recipe and inherited session/retry state. @author lare96 */
        class CraftArmorData : InventoryScriptData() {
            /** Armour enum retained for compatibility with existing snapshots. */
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

        /** Minimum consumable inputs for one conversion; thread is consumed by the player action. */
        fun supplies(armor: HideArmor): List<Item> = armor.hidesItem?.let {
            listOf(it, Item(THREAD_ID))
        } ?: when (armor) {
            HideArmor.STUDDED_BODY -> listOf(Item(HideArmor.LEATHER_BODY.id), Item(CraftStuddedActionItem.STUDS))
            HideArmor.STUDDED_CHAPS -> listOf(Item(HideArmor.LEATHER_CHAPS.id), Item(CraftStuddedActionItem.STUDS))
            else -> error("Invalid armour type $armor")
        }

        /** Required reusable tools; studded armour uses only its two consumable inputs. */
        private fun tools(armor: HideArmor): Set<Int> = if (armor.hides != null) setOf(NEEDLE_ID) else emptySet()

        /** Checks one complete recipe and its tools across owned inventory and bank stock. */
        fun hasSupplies(bot: Bot, armor: HideArmor): Boolean = bot.ownsProductionSupplies(supplies(armor), tools(armor))
    }

    /** Permanent Crafting level required to select and initialize this recipe. */
    val requiredLevel = armor.level
    /** Minimum consumables checked at startup and before each production interaction. */
    private val materials = supplies(armor)
    /** Reusable tool reserved during leather withdrawals. */
    private val requiredTools = tools(armor)

    /** Restores existing armour snapshots, including saved zones and shared retry counters. */
    constructor(bot: Bot, data: CraftArmorData) : this(bot, data.armor, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent level and owned minimum inputs before factory selection. */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel && hasSupplies(bot, armor)

    /**
     * Builds a capacity-safe partial batch. Stackable thread occupies one slot and is capped by bank stock,
     * with one thread per requested output as a conservative allowance for the player's variable consumption.
     * Non-stackable thread uses the ordinary balanced-input calculation.
     */
    fun bankBatch(): List<Item> {
        val hides = armor.hidesItem
        if (hides == null || !bot.inventory.stackable(THREAD_ID)) return bot.productionBatch(materials, requiredTools)
        val batch = bot.productionBatch(listOf(hides), setOf(NEEDLE_ID, THREAD_ID))
        val hideCount = batch.firstOrNull { it.id == hides.id }?.amount ?: return emptyList()
        val amount = minOf(hideCount / hides.amount, bot.bank.computeAmountForId(THREAD_ID))
        if (amount < 1) return emptyList()
        return listOf(Item(NEEDLE_ID), Item(hides.id, hides.amount * amount), Item(THREAD_ID, amount))
    }

    override fun withdraw(): List<Item> = productionWithdraw(materials, requiredTools,
        levelEligible = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether the carried inventory can complete at least one conversion. */
    private fun hasMaterials(): Boolean = bot.inventory.containsAll(materials) && requiredTools.all { bot.inventory.contains(it) }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_CRAFTING).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Opens the existing leather interface or starts the native studded-armour action. */
    private suspend fun startProduction(): Boolean {
        val hides = armor.hidesItem ?: return handler.inventory.useItem(CraftStuddedActionItem.STUDS).onItem(materials.first().id)
        if (!handler.inventory.useItem(NEEDLE_ID).onItem(hides.id)) return false
        val amount = bot.inventory.computeAmountForId(hides.id) / hides.amount
        if (armor.hides!!.first == Hide.SOFT_LEATHER) {
            if (!waitFor(3.seconds) { SoftLeatherInterface::class in bot.overlays }) return false
            val buttons = SoftLeatherInterface.BUTTON_MAP[armor] ?: return false
            output.clickButton(if (amount >= 10) buttons.first else if (amount >= 5) buttons.second else buttons.third)
        } else {
            if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
            val index = HideArmor.HIDE_TO_ARMOR[armor.hides.first]?.indexOf(armor.id) ?: return false
            if (index < 0) return false
            handler.widgets.clickMakeItem(index, amount)
        }
        return true
    }

    override suspend fun finish() {
        bot.actions.first(CraftArmorActionItem::class.java)?.interrupt()
        bot.actions.first(CraftStuddedActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): CraftArmorData = CraftArmorData().also {
        it.armor = armor
        saveInventoryState(it)
    }
}
