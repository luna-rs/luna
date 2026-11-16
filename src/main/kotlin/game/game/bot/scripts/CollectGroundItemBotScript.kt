package game.bot.scripts

import api.bot.script.TargetingZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.totalItemAmount
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CraftingScriptFactory
import game.bot.scripts.skills.CollectHidesBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.bot.Bot
import game.bot.scripts.trading.TradeBotScript
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Collects an existing ground resource without combat, using ordinary pickups and the shared zone/bank lifecycle.
 * Native visibility and inventory rules apply; disappearing drops only count when carried stock increases.
 * An empty or repeatedly unreachable field is abandoned through the inherited search timeout.
 *
 * The goal counts inventory, equipment and bank stock rather than adding another batch on every visit.
 * Collection never creates supplies, drops belongings for space, or attacks an NPC. Reaching the stock goal requests
 * a bank visit before completion. Cowhide preparation uses this runner when the permanent build disallows combat.
 * Cowhide visits stop when the bot reaches the early-combat cutoff, including after a saved script is restored.
 *
 * @property itemId The unnoted resource to collect.
 * @property stockTarget The total owned quantity at which collection ends.
 * @property sellOnCompletion Advertise collected surplus when this is a profit outing.
 * @author lare96
 */
class CollectGroundItemBotScript(
    bot: Bot,
    val itemId: Int,
    val stockTarget: Int,
    duration: Duration,
    zones: MutableList<SubZone>,
    val sellOnCompletion: Boolean = false
) : TargetingZonedBotScript<GroundItem>(bot, duration, zones) {

    /**
     * Serializable resource goal and inherited zone/session data; live ground references are never saved.
     *
     * @author lare96
     */
    class CollectionData : ZonedBotScriptData() {
        /**
         * Resource identifier, invalid until a complete collection snapshot is loaded.
         */
        var itemId = -1

        /**
         * Total owned stock goal, retained across pauses and reloads.
         */
        var stockTarget = 0

        /**
         * Whether this outing should offer its collected item at a marketplace when complete.
         */
        var sellOnCompletion = false

        override fun load(data: JsonObject) {
            super.load(data)
            itemId = data.get("item_id")?.asInt ?: -1
            stockTarget = data.get("stock_target")?.asInt ?: 0
            sellOnCompletion = data.get("sell_on_completion")?.asBoolean ?: false
        }

        override fun save(data: JsonObject) {
            super.save(data)
            data.addProperty("item_id", itemId)
            data.addProperty("stock_target", stockTarget)
            data.addProperty("sell_on_completion", sellOnCompletion)
        }
    }

    /**
     * Restores the same owned-stock goal and candidate fields after persistence.
     */
    constructor(bot: Bot, data: CollectionData) : this(bot, data.itemId, data.stockTarget, data.duration,
        data.zones, data.sellOnCompletion)

    override val targetSearchTimeout = 2.minutes

    override fun wildernessNotingItems(): Set<Int> = setOf(itemId)

    /**
     * Checks current ownership so other pickups and banking cannot restart or inflate the collection goal.
     */
    private fun goalReached(): Boolean = bot.totalItemAmount(itemId) >= stockTarget

    /**
     * A completed collection still deposits carried notes before handing over to the next activity.
     */
    private fun carryingResource(): Boolean = itemId in bot.inventory ||
        itemDef(itemId).notedId.orElse(-1) in bot.inventory

    private fun levelEligible(): Boolean = itemId != CollectHidesBotScript.COWHIDE ||
        CraftingScriptFactory.canCollectHides(bot)

    override suspend fun onInit(resumed: Boolean): Boolean =
        levelEligible() && ItemDefinition.isIdValid(itemId) && stockTarget > 0 &&
                (!goalReached() || carryingResource())

    override suspend fun equipment() = null

    override fun allowsLoot(item: GroundItem): Boolean = item.id == itemId && !goalReached()

    override suspend fun beforeZoneWork(): Boolean {
        if (!levelEligible()) {
            stop()
            return true
        }
        if (!goalReached()) return false
        if (carryingResource()) {
            forceBanking = true
            return false
        }
        queueSale()
        stop()
        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (goalReached()) {
            queueSale()
            stop()
        }
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<GroundItem> {
        val zone = activeZone ?: return mutableListOf()
        if (goalReached()) return mutableListOf()
        return world.locator.findItems(searchBase, searchRadius) {
            it.id == itemId && it.state == EntityState.ACTIVE && it.position in zone.area && it.isVisibleTo(bot)
        }
    }

    override suspend fun onAssignFocus(newFocus: GroundItem): Boolean {
        if (!levelEligible()) {
            stop()
            return false
        }
        if (bot.inventory.isFull || goalReached()) {
            if (goalReached()) forceBanking = true
            return false
        }
        return newFocus.id == itemId && newFocus.position in activeZone!!.area && handler.loot.canPickup(newFocus)
    }

    override suspend fun interactWithTarget(option: Int, target: GroundItem): Boolean {
        val zone = activeZone ?: return false
        return handler.loot.pickup(target) {
            levelEligible() && isActionSafe() && activeZone == zone && !goalReached() && target.position in zone.area
        }
    }

    override suspend fun refocus(): Boolean = true

    override suspend fun completed() {
        queueSale()
    }

    private fun queueSale() {
        if (!sellOnCompletion || !BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return
        val quote = BotTradeManager.computeTargetedSaleItem(bot, BotTradePrice.computePriceStrategy(bot), itemId)
        if (quote != null && quote.totalPrice().toLong() >= BotTradeManager.MINIMUM_OFFER_VALUE) {
            bot.scriptStack.softPushHead(TradeBotScript(bot, BotTradePurpose.SELL,
                BotMarketplace.select(bot), saleTarget = itemId))
        }
    }

    override fun snapshot(): CollectionData = CollectionData().also {
        it.itemId = itemId
        it.stockTarget = stockTarget
        it.sellOnCompletion = sellOnCompletion
        it.duration = duration
        it.zones = originalZones.toMutableList()
    }
}
