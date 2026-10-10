package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.crafting.potteryCrafting.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Shapes soft clay or fires owned unfired pottery using the existing player dialogues.
 *
 * All five [Unfired] recipes share this script. Shaping uses soft clay on a supported potter's wheel and
 * selects the recipe's slot in the full dialogue; firing uses the unfired item on a supported oven and
 * selects slot zero in its specialized dialogue. The player actions own conversions, level checks, XP,
 * animation, and timing. Each phase is independently eligible when its input is owned.
 *
 * The existing Barbarian Village zone supplies wheels and ovens, with Edgeville as its banking parent.
 * [InventoryBotScript] handles travel, unnoted withdrawal verification, safety checks, progress detection,
 * and persistent retry limits. Batches contain up to twenty-eight inputs, including partial bank stock.
 * Missing inputs request a total target of 1,000. Current-level loss ends the session; temporary unsafe
 * states defer work. Snapshots preserve the recipe, phase, duration, zones, and inherited retry counters.
 *
 * @param bot The bot processing pottery.
 * @property material Existing pottery recipe selected for this session.
 * @property stage Whether to shape soft clay or fire an unfired item.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Candidate pottery zones.
 * @author lare96
 */
class MakePotteryBotScript(
    bot: Bot,
    val material: Unfired,
    val stage: Stage,
    duration: Duration,
    zones: MutableList<SubZone> = DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Existing low-level pottery route, backed by Edgeville banking and travel. */
        val DEFAULT_ZONES = listOf(SubZone.BARBARIAN_VILLAGE)
        /** Soft clay consumed by the player shaping action. */
        const val SOFT_CLAY = 1761

        /**
         * Processing phase and object IDs accepted by the existing pottery interaction plugin.
         *
         * @property objectIds Supported wheel or oven definitions resolved inside the active zone.
         * @author lare96
         */
        enum class Stage(val objectIds: Set<Int>) {
            SHAPE(setOf(2642, 4310)),
            FIRE(setOf(2643, 4308, 11601))
        }

        /**
         * Recipe and phase stored alongside the inherited session configuration and retry budgets.
         *
         * @author lare96
         */
        class PotteryData : InventoryScriptData() {
            /** [Unfired] enum name used to restore the selected recipe. */
            var recipe = ""
            /** [Stage] enum name used to restore the processing phase. */
            var stage = ""
            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
                stage = data.get("stage")?.asString ?: ""
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
                data.addProperty("stage", stage)
            }
        }
    }

    /** Permanent Crafting level required for selection and startup. */
    val requiredLevel = material.level
    /** Input selected from the existing shaping/firing actions. */
    val inputId = if (stage == Stage.SHAPE) SOFT_CLAY else material.unfiredId
    /** Minimum carried input for one conversion. */
    private val materials = listOf(Item(inputId))

    /** Restores the selected recipe, processing phase, session configuration, and retry counters. */
    constructor(bot: Bot, data: PotteryData) :
        this(bot, Unfired.valueOf(data.recipe), Stage.valueOf(data.stage), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent Crafting level and owned inputs across inventory and bank for factory selection. */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel &&
        bot.ownsProductionSupplies(materials)

    /** Returns up to twenty-eight banked inputs, limited by available stock. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> =
        productionWithdraw(materials, levelEligible = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also { if (it.isEmpty()) stop() }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(bot.inventory.containsAll(materials))

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(bot.inventory.containsAll(materials),
                levelEligible = bot.skill(SKILL_CRAFTING).level >= requiredLevel)) return true
        attemptProduction(inputId) { startProduction() }
        return true
    }

    /** Opens the player dialogue on a supported loaded facility and selects the carried input batch. */
    private suspend fun startProduction(): Boolean {
        val zone = activeZone ?: return false
        val facility = world.locator.findObjects(zone.area.centerPosition, zone.area.tileRadius) {
            it.position in zone.area && it.id in stage.objectIds
        }.firstOrNull() ?: return false
        if (!handler.inventory.useItem(inputId).onObject(facility)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = bot.inventory.computeAmountForId(inputId)
        if (amount < 1) return false
        val index = if (stage == Stage.SHAPE) Unfired.UNFIRED_ID_ARRAY.indexOf(material.unfiredId) else 0
        handler.widgets.clickMakeItem(index, amount)
        return true
    }

    override suspend fun finish() {
        if (stage == Stage.SHAPE) bot.actions.first(PotteryWheelActionItem::class.java)?.interrupt()
        else bot.actions.first(PotteryOvenActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): PotteryData = PotteryData().also {
        it.recipe = material.name
        it.stage = stage.name
        saveInventoryState(it)
    }
}
