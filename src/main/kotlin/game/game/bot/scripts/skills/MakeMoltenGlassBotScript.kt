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
import game.skill.crafting.glassMaking.MakeMoltenGlassActionItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Smelts owned soda ash and buckets of sand through the normal furnace make-item dialogue.
 *
 * [InventoryBotScript] handles banking, zone travel, expiry, and weak-action gating. Balanced withdrawals
 * contain up to fourteen pairs, limited by the smaller bank stock. The player [MakeMoltenGlassActionItem]
 * consumes the inputs, returns empty buckets, and awards its existing twenty Crafting experience per glass.
 * No materials are gathered, bought, or injected; missing startup inputs request total targets of 1,000.
 *
 * Default zones are the existing Al Kharid and Falador furnace routes used by ore-smelting bots. Every
 * interaction resolves a loaded object named Furnace with a Smelt option inside the active zone. Missing
 * furnaces and interactions without input consumption share a three-attempt budget. Banking has a separate
 * persistent budget and verifies unnoted withdrawals within fifteen seconds. Unsafe bot states defer work.
 * Saved data retains duration, zones, and both budgets; live furnace objects are resolved after restoration.
 *
 * @param bot The bot producing molten glass.
 * @param duration Session duration managed by the inherited zone lifecycle.
 * @param zones Candidate banked furnace zones.
 * @author lare96
 */
class MakeMoltenGlassBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone> = DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /** Banked furnace routes already used by the existing ore-smelting factory. */
        val DEFAULT_ZONES = listOf(SubZone.AL_KHARID_BANK, SubZone.FALADOR_WEST_BANK)
        /** Soda ash consumed by the player action. */
        const val SODA_ASH = 1781
        /** Bucket of sand consumed by the player action. */
        const val BUCKET_OF_SAND = 1783

        /**
         * Saved furnace zones and retry counters alongside the inherited duration and candidate zones.
         * Counters survive resumption so restoring a script does not reset an exhausted retry budget.
         *
         * @author lare96
         */
        class MoltenGlassData : InventoryScriptData() {

            override fun load(data: JsonObject) {
                super.load(data)
            }

            override fun save(data: JsonObject) {
                super.save(data)
            }
        }
    }

    /** Permanent Crafting level required for selection and startup validation. */
    val requiredLevel = 1
    /** Minimum supplies for one conversion; both inputs are non-stackable. */
    private val materials = listOf(Item(SODA_ASH), Item(BUCKET_OF_SAND))

    /**
     * Restores the session configuration, and retry budgets from a snapshot.
     * Current supplies and bot safety are checked again by the normal initialization hooks.
     *
     * @param bot The bot that owns the saved script.
     * @param data Previously serialized molten-glass production state.
     */
    constructor(bot: Bot, data: MoltenGlassData) :
        this(bot, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Checks permanent level eligibility and one complete input pair across inventory and bank.
     * Temporary current-level, combat, lock, and strong-action restrictions are checked by lifecycle hooks.
     */
    fun isEligible(): Boolean = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel &&
            bot.ownsProductionSupplies(materials)

    /** Returns up to fourteen balanced input pairs from bank stock, or an empty batch if either input is absent. */
    fun bankBatch(): List<Item> = bot.productionBatch(materials)

    override fun withdraw(): List<Item> = productionWithdraw(materials, levelEligible = bot.skill(SKILL_CRAFTING).staticLevel >= requiredLevel)

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /** Whether inventory contains at least one complete pair of the configured recipe's inputs. */
    private fun hasMaterials() = bot.inventory.containsAll(materials)

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.skill(SKILL_CRAFTING).level >= requiredLevel)) return true
        attemptProduction(materials.first().id) { startProduction() }
        return true
    }

    /** Resolves a supported furnace, opens the player dialogue, and requests the carried balanced batch. */
    private suspend fun startProduction(): Boolean {
        val zone = activeZone ?: return false
        val furnace = world.locator.findObjects(zone.area.centerPosition, zone.area.tileRadius) {
            it.position in zone.area && it.def().name == "Furnace" && "Smelt" in it.def().actions
        }.firstOrNull() ?: return false
        if (!handler.inventory.useItem(SODA_ASH).onObject(furnace)) return false
        if (!waitFor(3.seconds) { MakeItemDialogue::class in bot.overlays }) return false
        val amount = materials.minOf { bot.inventory.computeAmountForId(it.id) / it.amount }
        if (amount < 1) return false
        handler.widgets.clickMakeItem(0, amount)
        return true
    }

    override suspend fun finish() {
        bot.actions.first(MakeMoltenGlassActionItem::class.java)?.interrupt()
    }

    override fun snapshot(): MoltenGlassData = MoltenGlassData().also {
        saveInventoryState(it)
    }
}
