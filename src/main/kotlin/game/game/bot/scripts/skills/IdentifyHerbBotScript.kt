package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.skill.herblore.identifyHerb.Herb
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Identifies one configured herb type through its normal first inventory option.
 *
 * [InventoryBotScript] supplies zone travel, banking, session expiry, and weak-action gating. After depositing
 * the previous inventory, the script withdraws up to twenty-eight unidentified herbs, allowing a smaller final
 * batch when bank stock runs low. Identification is an individual click rather than a make-item dialogue or
 * repeating action; the existing gameplay handler performs conversion, level validation, and experience awards.
 *
 * Eligibility requires an owned unidentified herb and the permanent Herblore level for its recipe. Missing
 * startup stock is added to wanted items before stopping. Current level is checked again during execution,
 * while combat, locks, and strong actions defer processing. Three consecutive interactions without input
 * consumption end the session; unresolved banking requests are bounded separately. A failed or timed-out
 * withdrawal ends the script through the inherited banking hook.
 *
 * Snapshots retain the herb recipe, duration, candidate zones, and retry counters. Inventory slots and other
 * live state are rebuilt on resumption. No tool, quest, or orb/potion preparation is required by this activity.
 *
 * @param bot The bot running this script.
 * @property herb The unidentified herb recipe to process throughout the session.
 * @param duration The session duration managed by the inherited lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class IdentifyHerbBotScript(
    bot: Bot,
    val herb: Herb,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Saved herb recipe and retry counters alongside inherited duration and candidate zones.
         * Restoring a session retains its exhausted budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class HerbData : InventoryScriptData() {
            /** [Herb] enum name used to reconstruct the configured recipe. */
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

    /** Permanent Herblore level required for selection and startup validation. */
    val requiredLevel = herb.level
    /** One unidentified herb, the minimum supply for a single conversion. */
    private val materials = listOf(herb.idItem)

    /**
     * Restores the herb, session configuration, and retry budgets from a snapshot.
     * Initialization still checks current owned supplies and bot safety before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized herb-identification state.
     */
    constructor(bot: Bot, data: HerbData) : this(bot, Herb.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent level eligibility and at least one unidentified herb across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to twenty-eight banked unidentified herbs, or an empty batch when stock is exhausted. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials, levelEligible = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains at least one unidentified herb of the configured type. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_HERBLORE).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Clicks the herb's first inventory option to invoke the existing identification handler once. */
    private suspend fun startProduction(): Boolean = handler.inventory.clickItem(1, herb.id)

    override fun snapshot(): HerbData = HerbData().also {
        it.recipe = herb.name
        saveInventoryState(it)
    }
}
