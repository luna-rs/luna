package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import game.skill.farming.Farming
import game.skill.farming.compostBin.CompostBin
import game.skill.farming.compostBin.CompostBinLocation
import game.skill.farming.compostBin.CompostBinType
import game.skill.farming.herb.HerbSeed
import game.skill.farming.tree.TreeSapling
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Visits a native compost bin to fill, close, open or empty it through ordinary player interactions.
 * Only owned weeds, tree roots and compostable farm produce are used; herbs and seeds are preserved. Inputs must
 * match the bin's existing type, and a new batch requires enough owned material to finish the whole bin. No native
 * contents are discarded or mixed down to ordinary compost. Existing tomato bins are left alone.
 *
 * Finished products and pending bin contents count toward a small fertilizer reserve. Full and partly filled bins
 * are completed even when that reserve is already met. Closed bins are left to rot while the bot does other work;
 * later visits collect finished compost with owned buckets. Native handlers alone exchange items, set timers and
 * award experience. Shared inventory lifecycle handles banking, travel, safety, retries and saved sessions.
 *
 * @param bot The gardener.
 * @property location Native persistent compost-bin identity.
 * @property type Fertilizer type to prepare, retained when a session is saved.
 * @param duration Maximum visit duration, including banking and travel.
 * @property target Desired reserve of this fertilizer across carried/banked stock and pending native bins.
 * @author lare96
 */
class MakeCompostBotScript(
    bot: Bot,
    val location: CompostBinLocation,
    val type: CompostBinType,
    duration: Duration,
    val target: Int = CompostBin.MAX_ITEMS * 2
) : InventoryBotScript(bot, duration, mutableListOf(SITES.getValue(location))) {
    override val trainingSkill = SKILL_FARMING
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Native cache bins within existing mapped farming zones. Port Phasmatys retains the Farming parent-zone TODO.
         */
        val SITES = linkedMapOf(
            CompostBinLocation.FALADOR to SubZone.FALADOR_FARM,
            CompostBinLocation.CATHERBY to SubZone.CATHERBY_FARM,
            CompostBinLocation.ARDOUGNE to SubZone.ARDOUGNE_FARM
        )

        /**
         * Native compostable farm products, excluding valuable herbs and non-compostable items. Weeds are preferred
         * for ordinary compost and roots for supercompost before other owned produce of the same native type.
         */
        private val MATERIALS: List<Int> = (listOf(Farming.WEEDS) + TreeSapling.entries.map { it.roots } +
            Farming.LOCATIONS.flatMap { it.createPatch().crops }.filterNot { it is HerbSeed }.map { it.produce.id })
            .distinct().filter { CompostBinType.lookup(it) in listOf(CompostBinType.COMPOST, CompostBinType.SUPERCOMPOST) }

        /**
         * Saves only visit policy and shared session/retry state. Native bin contents and rot timers remain in Farming.
         *
         * @author lare96
         */
        class CompostData : InventoryScriptData() {
            /**
             * Native bin location.
             */
            var location = CompostBinLocation.FALADOR
            /**
             * Fertilizer type; never a rotten-tomato recipe.
             */
            var type = CompostBinType.COMPOST
            /**
             * Desired fertilizer reserve, including bins already in progress.
             */
            var target = CompostBin.MAX_ITEMS * 2
            override fun load(data: JsonObject) {
                super.load(data)
                location = CompostBinLocation.valueOf(data.get("location").asString)
                type = CompostBinType.valueOf(data.get("type").asString)
                target = data.get("target").asInt
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("location", location.name)
                data.addProperty("type", type.name)
                data.addProperty("target", target)
            }
        }
    }

    init {
        require(type != CompostBinType.TOMATOES && target > 0)
    }

    /**
     * Reads native state on every decision so growth and changes made since a saved visit are respected.
     */
    val bin: CompostBin get() = Farming.compostBins(bot).getValue(location)

    /**
     * Restores visit policy without copying bin state or replenishing retry counters.
     */
    constructor(bot: Bot, data: CompostData) : this(bot, data.location, data.type, data.duration, data.target) {
        restoreInventoryState(data)
    }

    /**
     * Counts unnoted stock available to the native item handlers.
     */
    private fun owned(id: Int): Long = bot.bank.computeAmountForId(id).toLong() + bot.inventory.computeAmountForId(id)

    /**
     * Includes in-progress contents so three separate bins cannot each start another full reserve.
     */
    private fun reserved(): Long = owned(type.product) + Farming.compostBins(bot).values
        .filter { it.type == type }.sumOf { it.amount.toLong() }

    /**
     * Plans a complete next operation from the supplied stock view. Null means no useful supplied operation;
     * an empty list means the first object option closes a full bin. Ready bins bring as many owned buckets as fit.
     */
    private fun supplies(stock: (Int) -> Long): List<Item>? {
        val bin = bin
        if (bin.type != null && bin.type != type) return null
        if (bin.closed && !bin.isRotted(System.currentTimeMillis())) return null
        if (bin.open || bin.closed) {
            val buckets = minOf(bin.amount.toLong(), stock(Farming.BUCKET), bot.inventory.capacity().toLong()).toInt()
            return if (buckets > 0) listOf(Item(Farming.BUCKET, buckets)) else null
        }
        if (bin.canClose()) return emptyList()
        if (!bin.canFill() || bin.amount == 0 && reserved() >= target) return null
        var needed = CompostBin.MAX_ITEMS - bin.amount
        val inputs = buildList {
            for (id in MATERIALS) {
                if (CompostBinType.lookup(id) != type) continue
                val amount = minOf(needed.toLong(), stock(id)).toInt()
                if (amount > 0) { add(Item(id, amount)); needed -= amount }
                if (needed == 0) break
            }
        }
        return inputs.takeIf { needed == 0 }
    }

    /**
     * Selects only actionable visits, including state-only closing. Missing buckets or partial raw stock wait for
     * another activity to supply them instead of requesting free materials or repeatedly selecting an idle bin.
     */
    fun isEligible(): Boolean = supplies(::owned) != null

    override suspend fun equipment(): BotGearLocator? = null
    override suspend fun beforeInventoryInit(): Boolean {
        Farming.update(bot)
        return isEligible()
    }
    override fun withdraw(): List<Item> = productionWithdraw(supplies(::owned).orEmpty(), requestMissing = false)
    override fun bankWithdraw(): List<Item> = supplies { bot.bank.computeAmountForId(it).toLong() }
        ?: emptyList<Item>().also { stop() }

    override suspend fun onInventoryBankRequested(): Boolean =
        if (bin.closed && !bin.isRotted(System.currentTimeMillis())) false
        else requestProductionBank(supplies { bot.inventory.computeAmountForId(it).toLong() } != null)

    override suspend fun onExecuteInZone(): Boolean {
        Farming.update(bot)
        if (bin.closed && !bin.isRotted(System.currentTimeMillis())) return false
        val supplies = supplies { bot.inventory.computeAmountForId(it).toLong() }
        if (!productionReady(supplies != null)) return true
        val input = if (bin.closed || bin.canClose()) null else supplies?.firstOrNull()?.id
        val before = bin.amount
        val wasClosed = bin.closed
        val wasOpen = bin.open
        val zone = SITES.getValue(location)
        val obj = world.locator.findObjects(zone.inside, zone.area.tileRadius) {
            it.id == location.objectId && it.state == EntityState.ACTIVE && it.position.z == 0 && it in zone.area
        }.firstOrNull()
        attemptProduction(input ?: Farming.BUCKET, available = obj != null,
            outputId = type.product.takeIf { wasOpen },
            progressCondition = { bin.amount != before || bin.closed != wasClosed || bin.open != wasOpen }) {
            obj != null && if (input == null) handler.interactions.interact(1, obj)
            else handler.inventory.useItem(input).onObject(obj)
        }
        return true
    }

    override fun snapshot(): CompostData = CompostData().also {
        saveInventoryState(it)
        it.location = location
        it.type = type
        it.target = target
    }
}
