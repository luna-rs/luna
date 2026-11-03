package game.bot.scripts

import api.bot.action.BotLightSources
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.SubZone
import api.predef.firemaking
import com.google.gson.JsonObject
import game.content.darkness.DarkArea
import game.skill.firemaking.Firemaking
import game.skill.firemaking.lightSource.LightSource
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Prepares an explicitly wanted lit or unlit light stock from owned sources through native item interactions.
 * Candles and open lights are processed only in safe geography; extinguishing never removes illumination in a dark area.
 * Each bounded session has one direction and target, preventing ordinary activity selection from endlessly toggling stock.
 * The shared inventory lifecycle handles banking, travel, retries and saved sessions without awarding Firemaking XP.
 *
 * @param bot The bot preparing owned light stock.
 * @property source The native light-source recipe.
 * @property lighting Whether to light sources instead of extinguishing them.
 * @property target Wanted total output stock across inventory and bank.
 * @param duration Session length managed by the inventory lifecycle.
 * @param zones Existing safe stationary processing areas.
 * @author lare96
 */
class ToggleLightBotScript(
    bot: Bot,
    val source: LightSource,
    val lighting: Boolean,
    val target: Int,
    duration: Duration,
    zones: MutableList<SubZone> = StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
) : InventoryBotScript(bot, duration, zones) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Saved native source, conversion direction and fixed wanted-stock target.
         *
         * @author lare96
         */
        class LightData : InventoryScriptData() {
            /**
             * Native source enum name, retained independently of the current item form.
             */
            var source = ""
            /**
             * Fixed conversion direction for this saved session.
             */
            var lighting = true
            /**
             * Bounded total output stock requested when the session was selected.
             */
            var target = 1

            override fun load(data: JsonObject) {
                super.load(data)
                source = data.get("source")?.asString ?: ""
                lighting = data.get("lighting")?.asBoolean ?: true
                target = (data.get("target")?.asInt ?: 1).coerceIn(1, 1_000)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("source", source)
                data.addProperty("lighting", lighting)
                data.addProperty("target", target)
            }
        }
    }

    init {
        require(target in 1..1_000)
    }

    /**
     * One source is replaced per operation; only lighting retains a tinderbox.
     */
    private val inputId = if (lighting) source.unlit else source.lit
    private val outputId = if (lighting) source.lit else source.unlit
    private val materials = listOf(Item(inputId))
    private val tools = if (lighting) setOf(Firemaking.TINDERBOX) else emptySet()

    /**
     * Restores the fixed conversion and inherited retry budgets.
     */
    constructor(bot: Bot, data: LightData) :
        this(bot, LightSource.valueOf(data.source), data.lighting, data.target, data.duration, data.zones) {
        restoreInventoryState(data)
    }

    /**
     * Remaining wanted output, independent of the stock of unopened sources.
     */
    private fun remaining(): Int = (target.toLong() - bot.bank.computeAmountForId(outputId) -
        bot.inventory.computeAmountForId(outputId)).coerceIn(0L, target.toLong()).toInt()

    /**
     * Preserves explicitly wanted input stock so competing lit and unlit targets cannot toggle the same lights forever.
     */
    private fun convertible(): Int {
        val reserve = bot.preferences.getWantedItem(inputId)?.target()?.coerceAtLeast(0) ?: 0
        val surplus = bot.bank.computeAmountForId(inputId).toLong() + bot.inventory.computeAmountForId(inputId) - reserve
        return minOf(remaining().toLong(), surplus.coerceAtLeast(0)).toInt()
    }

    /**
     * Requires owned inputs and tools and, when lighting, the real current native Firemaking requirement.
     */
    fun isEligible(): Boolean = convertible() > 0 && (!lighting || bot.firemaking.level >= source.level) &&
        bot.ownsProductionSupplies(materials, tools)

    /**
     * Bounds owned bank inputs by inventory capacity and the remaining explicit wanted-stock target.
     */
    fun bankBatch(): List<Item> {
        val available = convertible()
        if (available == 0) return emptyList()
        return bot.productionBatch(materials, tools).map { if (it.id == inputId) Item(inputId, minOf(it.amount, available)) else it }
    }

    override fun withdraw(): List<Item> {
        if (convertible() == 0) {
            stop()
            return emptyList()
        }
        return productionWithdraw(materials, tools, levelEligible = !lighting || bot.firemaking.level >= source.level,
            requestMissing = false)
    }

    override fun bankWithdraw(): List<Item> = bankBatch().also {
        if (it.isEmpty()) stop()
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    /**
     * Native light transformations do not expand the source inventory footprint.
     */
    private fun hasMaterials(): Boolean = bot.inventory.contains(inputId) && tools.all { bot.inventory.contains(it) }

    override suspend fun onExecuteInZone(): Boolean {
        if (convertible() == 0) {
            stop()
            return true
        }
        if (!productionReady(hasMaterials(), levelEligible = !lighting || bot.firemaking.level >= source.level)) return true
        if (lighting && !BotLightSources.safeToLight(bot, source) ||
            !lighting && DarkArea.forPosition(bot.position) != null) return false
        attemptProduction(inputId) {
            if (lighting) BotLightSources.light(bot, source) else BotLightSources.extinguish(bot, source)
        }
        return true
    }

    override fun snapshot(): LightData = LightData().also {
        it.source = source.name
        it.lighting = lighting
        it.target = target
        saveInventoryState(it)
    }
}
