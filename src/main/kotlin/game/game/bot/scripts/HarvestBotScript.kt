package game.bot.scripts

import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScriptData
import api.bot.script.TargetingZonedBotScript
import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.SkillingScriptFactory
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.profitMode
import game.bot.scripts.skills.MillFlourBotScript
import game.bot.scripts.skills.SpinTextileBotScript
import game.bot.scripts.trading.TradeBotScript
import game.obj.resource.harvestable.CabbageResource
import game.obj.resource.harvestable.FlaxResource
import game.obj.resource.harvestable.HarvestableResource
import game.obj.resource.harvestable.HarvestActionItem
import game.obj.resource.harvestable.OnionResource
import game.obj.resource.harvestable.PotatoResource
import game.obj.resource.harvestable.WheatResource
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A generic object-targeting script for harvesting simple world resources.
 *
 * This script is used for low-complexity resources that can be harvested directly from world objects, such as cabbages,
 * flax, onions, potatoes, and wheat. It searches for matching objects inside the active [SubZone], filters them through
 * the selected [HarvestableResource], and lets [TargetingZonedBotScript] handle target selection, interaction retries,
 * focus caching, and zone switching.
 *
 * @param bot The bot running this script.
 * @param harvestable The resource type this script should harvest.
 * @param duration How long this script should run before completing normally.
 * @param zones The candidate zones this script may harvest in.
 * @param rawProfit Harvest raw flax for sale without a skill preference or follow-up processing.
 * @author lare96
 */
class HarvestBotScript(
    bot: Bot,
    val harvestable: Harvestable,
    duration: Duration,
    zones: MutableList<SubZone>,
    val targetAmount: Int = 0,
    private var supplyDeadline: Long = 0L,
    val rawProfit: Boolean = false
) : TargetingZonedBotScript<GameObject>(bot, duration, zones) {
    init {
        require(targetAmount >= 0 && (targetAmount == 0 || harvestable == Harvestable.FLAX))
        require(!rawProfit || (harvestable == Harvestable.FLAX && targetAmount > 0))
    }

    companion object {

        /**
         * A simple enum wrapper around harvestable world resource definitions.
         *
         * The enum gives script data a stable, serializable value while each [HarvestableResource] implementation handles
         * the actual object-definition matching and resource-specific behavior.
         *
         * @property resource The resource definition used to identify valid harvest objects.
         */
        enum class Harvestable(val resource: HarvestableResource) {

            /**
             * Harvests cabbage field objects.
             */
            CABBAGE(CabbageResource),

            /**
             * Harvests flax field objects.
             */
            FLAX(FlaxResource),

            /**
             * Harvests onion field objects.
             */
            ONION(OnionResource),

            /**
             * Harvests potato field objects.
             */
            POTATO(PotatoResource),

            /**
             * Harvests wheat field objects.
             */
            WHEAT(WheatResource)
        }

        /**
         * Serializable script data for [HarvestBotScript].
         *
         * This stores the selected [Harvestable] resource along with the inherited zone and duration data needed to
         * recreate the script after persistence.
         */
        class HarvestData : ZonedBotScriptData() {

            /**
             * The resource type this script should harvest.
             */
            var harvestable: Harvestable? = null
            var targetAmount = 0
            var supplyDeadline = 0L
            var rawProfit = false

            override fun load(data: JsonObject) {
                super.load(data)
                harvestable = Harvestable.valueOf(data.get("resource").asString)
                targetAmount = data.get("targetAmount")?.asInt ?: 0
                supplyDeadline = data.get("supplyDeadline")?.asLong ?: 0L
                rawProfit = data.get("rawProfit")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("resource", harvestable!!.name)
                data.addProperty("targetAmount", targetAmount)
                data.addProperty("supplyDeadline", supplyDeadline)
                data.addProperty("rawProfit", rawProfit)
            }
        }
    }

    /**
     * Recreates a harvest script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved harvest script data.
     */
    constructor(bot: Bot, data: HarvestData) : this(bot, data.harvestable!!, data.duration, data.zones,
        data.targetAmount, data.supplyDeadline, data.rawProfit)

    /**
     * Whether this bot spins each inventory of flax at the Seers' Village wheel before banking it.
     */
    private var spinFlax = false

    private fun quotaReached(): Boolean = targetAmount > 0 &&
        bot.inventory.computeAmountForId(SpinTextileBotScript.FLAX).toLong() +
        bot.bank.computeAmountForId(SpinTextileBotScript.FLAX) >= targetAmount

    private fun supplyAllowed(): Boolean = targetAmount == 0 || (rawProfit ||
        BotCombatActionHandler.mayGainExperience(bot, SKILL_CRAFTING) &&
            (bot.profitMode || BotCombatActionHandler.wantsToTrain(bot, SKILL_FLETCHING))) &&
        (supplyDeadline == 0L || System.currentTimeMillis() < supplyDeadline)

    override suspend fun onInit(resumed: Boolean): Boolean {
        if (supplyDeadline > 0) duration = minOf(duration,
            (supplyDeadline - System.currentTimeMillis()).milliseconds)
        if (duration <= Duration.ZERO || !supplyAllowed() || quotaReached()) return false
        if (harvestable == Harvestable.FLAX && !rawProfit) {
            SpinTextileBotScript.leaveSeersWheel(bot)
            if (!resumed && targetAmount == 0) {
                spinFlax = bot.crafting.staticLevel >= Textile.BOWSTRING.level && rand(bot.personality.intelligence)
            }
        }
        return true
    }

    override suspend fun onPrepareTargeting(): Boolean {
        if (!supplyAllowed() || quotaReached()) {
            stopSupplyHarvest()
            stop()
            return false
        }
        return true
    }

    override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        if (!supplyAllowed() || quotaReached()) {
            stopSupplyHarvest()
            stop()
            return false
        }
        if (!initial && spinFlax && activeZone == SubZone.SOUTH_SEERS_VILLAGE_FLAX &&
            SpinTextileBotScript.FLAX in bot.inventory) {
            spinFlax = SpinTextileBotScript.spinAtSeers(bot)
        }
        return true
    }

    // Don't start another plant with a full inventory. Its click would cancel the walk to the bank.
    override suspend fun interactionOption(target: GameObject): Int? =
        if (bot.inventory.isFull || !supplyAllowed() || quotaReached()) null else 2

    private fun stopSupplyHarvest() {
        if (targetAmount > 0) bot.actions.first(HarvestActionItem::class.java)
            ?.takeIf { it.resource == harvestable.resource }?.interrupt()
    }

    override suspend fun finish() {
        stopSupplyHarvest()
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        return world.locator.findObjects(searchBase, searchRadius, true) { harvestable.resource.isResource(it.def()) }
    }

    override fun snapshot(): BotScriptData {
        val data = HarvestData()
        data.harvestable = harvestable
        data.duration = duration
        data.zones = zones
        data.targetAmount = targetAmount
        data.supplyDeadline = supplyDeadline
        data.rawProfit = rawProfit
        return data
    }

    override suspend fun completed() {
        if (rawProfit) {
            if (BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) {
                val quote = BotTradeManager.computeTargetedSaleItem(bot,
                    BotTradePrice.computePriceStrategy(bot), SpinTextileBotScript.FLAX)
                if (quote != null && quote.totalPrice().toLong() >= BotTradeManager.MINIMUM_OFFER_VALUE) {
                    bot.scriptStack.softPushHead(TradeBotScript(bot, BotTradePurpose.SELL,
                        BotMarketplace.select(bot), saleTarget = SpinTextileBotScript.FLAX))
                }
            }
            return
        }
        if (targetAmount > 0) return
        // Chance to queue a flax spinning script, unless this bot spins its own flax.
        if (harvestable == Harvestable.FLAX && !spinFlax &&
            (rand(bot.personality.intelligence) || bot.personality.isDextrous)) {
            val script = SpinTextileBotScript(bot, SkillingScriptFactory.getDuration(bot))
            bot.scriptStack.pushTail(script, 2)
        }
        // Chance to queue milling the wheat into flour.
        if (harvestable == Harvestable.WHEAT && (rand(bot.personality.intelligence) || bot.personality.isDextrous)) {
            val mill = MillFlourBotScript.pickMill(bot)
            bot.scriptStack.pushTail(MillFlourBotScript(bot, mill, SkillingScriptFactory.getDuration(bot)), 2)
        }
    }
}
