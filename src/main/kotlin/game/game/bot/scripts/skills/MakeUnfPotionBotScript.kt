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
import game.skill.herblore.makeUnfPotion.MakeUnfActionItem
import game.skill.herblore.makeUnfPotion.UnfPotion
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Combines one configured identified herb with vials of water through the normal make-item dialogue.
 *
 * [InventoryBotScript] supplies banking, zone travel, session expiry, and weak-action gating. After depositing
 * the previous inventory, each bank visit withdraws up to fourteen herbs and fourteen vials of water. The
 * smaller available stock limits the batch, allowing remaining partial batches without requesting absent inputs.
 * No reusable tool is needed; each conversion consumes one herb and one water vial.
 *
 * The existing [MakeUnfActionItem] validates the recipe's Herblore level and converts the inputs without
 * awarding experience. This script prepares owned supplies and is selected only by the factory's profit mode.
 * It does not purchase inputs or assume a market margin. Missing startup ingredients are added to wanted
 * items before stopping, and current Herblore level is checked again before each production interaction.
 *
 * Three consecutive interactions without input consumption end the session. Unresolved banking requests
 * have a separate retry budget. Withdrawals are unnoted, verified, and bounded to fifteen seconds; failure ends
 * the script through the inherited banking hook. Snapshots retain the recipe, duration, candidate zones, and
 * retry counters rather than live inventory slots or actions.
 *
 * @param bot The bot running this script.
 * @property potion The unfinished-potion recipe to prepare throughout the session.
 * @param duration The session duration managed by the inherited lifecycle.
 * @param zones Candidate processing zones with existing banking and travel support.
 * @author lare96
 */
class MakeUnfPotionBotScript(
    bot: Bot,
    val potion: UnfPotion,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Saved recipe and retry counters alongside inherited duration and candidate zones.
         * Restoring a session preserves its exhausted budgets instead of granting fresh attempts.
         *
         * @author lare96
         */
        class UnfPotionData : InventoryScriptData() {
            /** [UnfPotion] enum name used to reconstruct the configured recipe. */
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
    val requiredLevel = potion.level
    /** One identified herb and one water vial, the minimum supplies for a single conversion. */
    private val materials = listOf(potion.herbItem, Item(UnfPotion.VIAL_OF_WATER))

    /**
     * Restores the recipe, session configuration, and retry budgets from saved state.
     * Initialization still checks current supplies and bot safety before the restored script can run.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized unfinished-potion state.
     */
    constructor(bot: Bot, data: UnfPotionData) :
        this(bot, UnfPotion.valueOf(data.recipe), data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /** Checks permanent level eligibility and one complete input pair across inventory and bank. */
    fun isEligible(): Boolean = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced input pairs, or an empty batch when either banked ingredient is missing. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials, levelEligible = bot.skill(SKILL_HERBLORE).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory currently holds a complete pair of the configured recipe's inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_HERBLORE).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Uses a water vial on the identified herb, waits for the single-recipe dialogue, and requests the batch. */
    private suspend fun startProduction(): Boolean {
        if (!handler.inventory.useItem(UnfPotion.VIAL_OF_WATER).onItem(potion.herb)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(MakeUnfActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): UnfPotionData = UnfPotionData().also {
        it.recipe = potion.name
        saveInventoryState(it)
    }
}
