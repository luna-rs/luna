package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.crafting.glassMaking.GlassBlowingActionItem
import game.skill.crafting.glassMaking.GlassMaterial
import game.skill.crafting.glassMaking.GlassBlowingInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Blows owned molten glass into one configured [GlassMaterial] using a reusable glassblowing pipe.
 *
 * Uses the player pipe-on-glass interaction to open [GlassBlowingInterface], then the recipe's existing
 * Make 1, Make 5, or Make 10 button according to remaining glass. [GlassBlowingActionItem] owns conversion,
 * current-level checks, animation, and XP. Recipes range from Crafting level 1 to 49 and include beer glasses,
 * candle lanterns, oil lamps, vials, fishbowls, unpowered orbs, and lantern lenses.
 *
 * [InventoryBotScript] handles banking, travel, session expiry, and weak-action gating in existing processing
 * zones. Bank batches reserve one pipe and withdraw up to twenty-seven molten glass, including partial stock.
 * The script does not smelt or buy glass. Missing startup supplies request total targets of 1,000 glass or
 * three pipes, then stop. Factory selection requires the permanent recipe level and supplies in bank or inventory.
 *
 * Death, locks, combat, and strong actions prevent startup or interaction. Current-level loss ends the session.
 * Three interactions without input consumption or unresolved banking requests exhaust separate retry budgets.
 * Unnoted withdrawals are verified within fifteen seconds. Snapshots preserve the material, duration, zones,
 * and budgets; finishing interrupts the active glassblowing action before coordinator selection resumes.
 *
 * @param bot The bot blowing glass.
 * @property material The existing product recipe selected for this session.
 * @param duration Session duration managed by the inherited lifecycle.
 * @param zones Existing banked processing zones available to the script.
 * @author lare96
 */
class BlowGlassBotScript(
    bot: Bot,
    val material: GlassMaterial,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Molten glass consumed by the existing player action. */
        const val MOLTEN_GLASS = 1775
        /** Glassblowing pipe retained during production. */
        const val PIPE = 1785

        /**
         * Serializable recipe and retry state, alongside the inherited duration and candidate zones.
         * Failure counters survive resumption so restarting a saved script cannot reset an exhausted budget.
         *
         * @author lare96
         */
        class GlassData : InventoryScriptData() {
            /** [GlassMaterial] enum name used to reconstruct the configured recipe. */
            var recipe = ""

            override fun load(data: JsonObject) {
                super.load(data)
                recipe = data.get("recipe")?.asString ?: ""
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("recipe", recipe)
            }
        }
    }

    /** Static Crafting level required for factory selection and startup validation. */
    val requiredLevel = material.level
    /** Minimum input for one conversion; the bank batch expands this to available inventory capacity. */
    private val materials = listOf(Item(MOLTEN_GLASS))
    /** Reusable tools reserved in every batch and retained by the glassblowing action. */
    private val tools = setOf(PIPE)

    /**
     * Restores the recipe, session configuration, and failure budgets from a saved snapshot.
     * Startup still validates current supplies and bot state before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized glassblowing state.
     */
    constructor(bot: Bot, data: GlassData) : this(bot, GlassMaterial.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Checks permanent level eligibility and minimum supplies across inventory and bank for factory selection.
     * Temporary combat, lock, action, and current-level restrictions are checked by lifecycle hooks.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials, tools)

    /** Returns one pipe and as much banked molten glass as fit, or an empty batch when either supply is unavailable. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials, tools)

    override fun withdraw(): List<Item> =
        productionWithdraw(materials, tools, levelEligible = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory currently holds a pipe and at least one molten glass. */
    private fun hasMaterials() = bot.inventory.containsAll(materials) && tools.all { it in bot.inventory }

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_CRAFTING).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Opens the glassblowing interface and selects a supported fixed-size batch for the remaining stock. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(PIPE).onItem(MOLTEN_GLASS)) return false
        if (!waitFor(3.seconds) { GlassBlowingInterface::class in bot.overlays }) return false
        val amount = bot.inventory.computeAmountForId(MOLTEN_GLASS)
        val button = when {
            amount >= 10 -> material.make10Id
            amount >= 5 -> material.make5Id
            else -> material.make1Id
        }
        output.clickButton(button)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(GlassBlowingActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): GlassData = GlassData().also {
        it.recipe = material.name
        saveInventoryState(it)
    }
}
