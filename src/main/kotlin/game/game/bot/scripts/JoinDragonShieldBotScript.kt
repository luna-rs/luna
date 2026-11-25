package game.bot.scripts

import api.bot.action.BotSupplySources
import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.ProfitGatheringGoal
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionBatch
import api.bot.zone.ProductionZones
import api.bot.zone.SubZone
import api.predef.ext.canUseWantedItem
import api.predef.smithing
import com.google.gson.JsonObject
import game.bot.scripts.trading.TradeBotScript
import game.skill.smithing.Smithing
import game.skill.smithing.Smithing.DRAGON_SHIELD_LEFT_HALF as LEFT_HALF
import game.skill.smithing.Smithing.DRAGON_SHIELD_LEVEL as LEVEL
import game.skill.smithing.Smithing.DRAGON_SHIELD_RIGHT_HALF as RIGHT_HALF
import game.skill.smithing.Smithing.DRAGON_SQUARE_SHIELD as SHIELD
import game.skill.smithing.Smithing.HAMMER
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivityWeights
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Smiths dragon shield halves in balanced batches and offers surplus shields through native trading.
 *
 * @author lare96
 */
class JoinDragonShieldBotScript(
    bot: Bot,
    duration: Duration,
    zones: MutableList<SubZone> = ProductionZones.ANVILS.toMutableList()
) : InventoryBotScript(bot, duration, zones) {

    companion object {
        private val materials = listOf(Item(LEFT_HALF), Item(RIGHT_HALF))
        private val tools = setOf(HAMMER)

        /**
         * Requires an owned left half and hammer, and an owned right half or an affordable first shield.
         */
        fun eligible(bot: Bot): Boolean = hasBaseSupplies(bot) &&
            (ProfitGatheringGoal.physical(bot, RIGHT_HALF) > 0 || rightHalfPurchaseLimit(bot) != null)

        private fun hasBaseSupplies(bot: Bot): Boolean =
            bot.smithing.staticLevel >= LEVEL && bot.smithing.level >= LEVEL &&
            ProfitGatheringGoal.physical(bot, LEFT_HALF) > 0 &&
            ProfitGatheringGoal.physical(bot, HAMMER) > 0

        /**
         * Quotes a single missing right half for personal use while preserving the bot's cash reserve.
         */
        private fun rightHalfPurchaseLimit(bot: Bot): Int? {
            if (ProfitGatheringGoal.physical(bot, SHIELD) > 0 || !bot.canUseWantedItem(SHIELD)) return null
            val shieldValue = bot.world.economy.getPrice(SHIELD)
            val spendable = ProfitGatheringGoal.physical(bot, Currency.COINS.id) - BotActivityWeights.STARTER_COIN_TARGET
            val limit = minOf(spendable, shieldValue, Int.MAX_VALUE.toLong())
            if (limit < 1L) return null
            return BotSupplySources.visits(bot, RIGHT_HALF, 1).minOfOrNull { it.unitPrice }
                ?.takeIf { it <= limit }?.toInt()
        }

        /**
         * Preserves the stock baseline so native repair can finish safely during a pause.
         *
         * @author lare96
         */
        class DragonShieldData : InventoryScriptData() {
            var openingStock = 0L

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("opening_stock", openingStock)
            }

            override fun load(data: JsonObject) {
                super.load(data)
                openingStock = data.get("opening_stock").asLong
                require(openingStock >= 0)
            }
        }
    }

    constructor(bot: Bot, data: DragonShieldData) : this(bot, data.duration, data.zones) {
        restoreInventoryState(data)
        openingStock = data.openingStock
    }

    override val verifiedProductionWithdrawals = true
    override val ownedSuppliesOnly = true
    override val preferNearbyWorkZone = true
    override val allowsPaidTravel = false
    private var openingStock = ProfitGatheringGoal.physical(bot, SHIELD)

    override suspend fun beforeInventoryInit(): Boolean {
        if (!hasBaseSupplies(bot)) return false
        if (ProfitGatheringGoal.physical(bot, RIGHT_HALF) < 1L) {
            val limit = rightHalfPurchaseLimit(bot) ?: return false
            if (RIGHT_HALF !in handler.shop.obtainWantedItems(mapOf(RIGHT_HALF to 1), limit,
                    mapOf(RIGHT_HALF to limit.toLong()))) return false
        }
        return bot.ownsProductionSupplies(materials, tools) || handler.banking.travelToBankDepositAll()
    }

    override fun withdraw(): List<Item> =
        productionWithdraw(materials, tools, levelEligible = bot.smithing.level >= LEVEL, requestMissing = false)

    override fun bankWithdraw(): List<Item> = bot.productionBatch(materials, tools).also {
        if (it.isEmpty()) {
            queueSale()
            stop()
        }
    }

    private fun hasMaterials() = bot.inventory.containsAll(materials) && bot.inventory.contains(HAMMER)

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasMaterials())

    override suspend fun onExecuteInZone(): Boolean {
        if (!productionReady(hasMaterials(), levelEligible = bot.smithing.level >= LEVEL)) return true
        val zone = activeZone ?: return false
        val anvil = bot.world.locator.findObjects(zone.area.centerPosition, zone.area.tileRadius) {
            it.id in Smithing.ANVIL_OBJECTS && it.position in zone.area
        }.minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return false

        attemptProduction(LEFT_HALF, outputId = SHIELD) {
            handler.inventory.useItem(LEFT_HALF).onObject(anvil) {
                isActionSafe() && hasMaterials() && bot.smithing.level >= LEVEL
            }
        }
        return true
    }

    override suspend fun completed() {
        queueSale()
    }

    private fun queueSale() {
        if (bot.state != EntityState.ACTIVE || !isActionSafe() || ProfitGatheringGoal.physical(bot, SHIELD) <= openingStock ||
            !BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return
        val quote = BotTradeManager.computeTargetedSaleItem(bot, BotTradePrice.computePriceStrategy(bot), SHIELD)
            ?: return
        if (!BotTradeManager.hasNativeSaleStock(bot, SHIELD, quote.item.amount) ||
            quote.totalPrice().toLong() < BotTradeManager.MINIMUM_OFFER_VALUE) return
        bot.scriptStack.softPushHead(TradeBotScript(bot, BotTradePurpose.SELL, BotMarketplace.select(bot),
            duration = 10.minutes, saleTarget = SHIELD))
    }

    override fun snapshot(): DragonShieldData = DragonShieldData().also {
        saveInventoryState(it)
        it.openingStock = openingStock
    }
}
