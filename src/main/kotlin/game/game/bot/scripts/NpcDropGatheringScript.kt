package game.bot.scripts

import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.zone.SubZone
import api.predef.ext.totalItemAmount
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.SetMultimap
import com.google.gson.JsonObject
import engine.bot.gear.BotGearPurpose
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import game.bot.scripts.trading.TradeBotScript
import game.skill.slayer.SlayerMaster
import game.skill.slayer.SlayerTaskType
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Kills named NPCs in supplied camps and banks one selected drop until the bot owns [stockTarget].
 * The inherited combat trip equips owned gear and brings food; this session never buries collected bones.
 * A profit outing advertises available stock through the normal player marketplace after banking or completion.
 *
 * @property itemId The unnoted item collected from NPC drops.
 * @property stockTarget Total banked and carried stock at which the trip ends.
 * @property sellOnCompletion Whether to offer the gathered item for sale.
 * @author lare96
 */
class NpcDropGatheringScript(
    bot: Bot,
    val itemId: Int,
    val stockTarget: Int,
    duration: Duration,
    zones: MutableList<SubZone>,
    private val targetNames: SetMultimap<SubZone, String>,
    val sellOnCompletion: Boolean = true,
    private val slayerTask: SlayerTaskType? = null,
    private val slayerMaster: SlayerMaster? = null
) : NpcCombatScript(bot, duration, zones, targetNames, BotGearPurpose.MELEE, null,
    slayerTask, slayerMaster) {

    /**
     * Persists the exact target and NPC restrictions alongside the inherited combat session.
     *
     * @author lare96
     */
    class DropGatheringData : NpcCombatData() {
        var itemId = -1
        var stockTarget = 0
        var sellOnCompletion = true

        override fun load(data: JsonObject) {
            super.load(data)
            itemId = data.get("item_id")?.asInt ?: -1
            stockTarget = data.get("stock_target")?.asInt ?: 0
            sellOnCompletion = data.get("sell_on_completion")?.asBoolean ?: true
        }

        override fun save(data: JsonObject) {
            super.save(data)
            data.addProperty("item_id", itemId)
            data.addProperty("stock_target", stockTarget)
            data.addProperty("sell_on_completion", sellOnCompletion)
        }
    }

    constructor(bot: Bot, data: DropGatheringData) : this(bot, data.itemId, data.stockTarget,
        data.duration, data.zones, data.names, data.sellOnCompletion, data.slayerTask, data.slayerMaster)

    private fun goalReached(): Boolean = bot.totalItemAmount(itemId) >= stockTarget

    private val notedItemId: Int get() = api.predef.itemDef(itemId).notedId.orElse(-1)

    private fun carryingResource(): Boolean = itemId in bot.inventory ||
        notedItemId in bot.inventory

    override suspend fun onInit(resumed: Boolean): Boolean =
        ItemDefinition.isIdValid(itemId) && stockTarget > 0 && originalZones.isNotEmpty() &&
            originalZones.all { targetNames[it].isNotEmpty() } &&
            ((slayerTask == null) == (slayerMaster == null)) &&
            super.onInit(resumed)

    override fun wildernessNotingItems(): Set<Int> = setOf(itemId)

    override fun isLoot(item: GroundItem): Boolean =
        (item.id == itemId || item.id == notedItemId) && !goalReached()

    override fun buryLootedBones(): Boolean = false

    override suspend fun onAssignFocus(newFocus: Npc): Boolean {
        if (bot.inventory.isFull || goalReached()) {
            forceBanking = true
            return false
        }
        return super.onAssignFocus(newFocus)
    }

    override suspend fun beforeZoneWork(): Boolean {
        if (goalReached()) {
            if (carryingResource()) {
                forceBanking = true
            } else {
                queueSale()
                stop()
                return true
            }
        }
        return super.beforeZoneWork()
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (goalReached()) {
            queueSale()
            stop()
            return
        }
        super.onBankOpen(initial)
    }

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

    override fun snapshot(): DropGatheringData = DropGatheringData().also {
        it.itemId = itemId
        it.stockTarget = stockTarget
        it.sellOnCompletion = sellOnCompletion
        it.slayerTask = slayerTask
        it.slayerMaster = slayerMaster
        it.duration = duration
        it.zones = originalZones.toMutableList()
        it.names = ImmutableSetMultimap.copyOf(targetNames)
    }
}
