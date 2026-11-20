package api.bot.script

import api.bot.action.trading.ProfitGatheringDemand
import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import game.bot.scripts.trading.NativeProductionSaleScript
import game.bot.scripts.trading.TradeBotScript
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.bot.Bot
import engine.bot.gear.BotItemTracker.Companion.itemOwnershipRevision
import java.util.UUID
import kotlin.time.Duration

/**
 * Keeps elective profit gathering tied to a funded consumer and one finite output allowance.
 * Training and personal production gathering never receive this authorization.
 *
 * @author lare96
 */
class ProfitGatheringGoal(val identity: String, val itemId: Int, val outputs: Set<Int>,
                          maximumProduced: Int, private var lastPhysicalStock: Long,
                          val deadline: Long, val minimumUnitPrice: Long, produced: Int = 0,
                          val marketOnly: Boolean = false, private val openingOutputStock: Long = 0L) {
    private var nextReview = 0L
    private var rejected = false
    private var ownershipRevision = Long.MIN_VALUE
    var maximumProduced: Int = maximumProduced
        private set
    var produced: Int = produced
        private set

    init {
        require(UUID.fromString(identity).toString() == identity)
        require(itemId >= 0 && itemId in outputs && outputs.size in 1..16)
        require(maximumProduced in 1..56 && produced in 0..maximumProduced && lastPhysicalStock >= 0L &&
            deadline > 0 && minimumUnitPrice > 0 && openingOutputStock >= 0L)
    }

    /**
     * Counts only gains since the previous physical observation; losses cannot renew the original allowance.
     * Ownership revisions avoid rescanning unchanged inventory and bank contents during native action polling.
     */
    fun observe(bot: Bot) {
        val revision = bot.itemOwnershipRevision
        if (ownershipRevision == revision) return
        val current = outputs.sumOf { physical(bot, it) }
        val gained = (current - lastPhysicalStock).coerceAtLeast(0L)
        produced += minOf(gained, (maximumProduced - produced).toLong()).toInt()
        lastPhysicalStock = current
        ownershipRevision = revision
    }

    /**
     * Rechecks monotonic output before another interaction, and external demand at most once every ten seconds.
     * A failed review ends this purpose permanently rather than renewing it after a pause.
     */
    fun canContinue(bot: Bot, now: Long = System.currentTimeMillis()): Boolean {
        observe(bot)
        if (rejected || now >= deadline || produced >= maximumProduced) return false
        if (now < nextReview) return true
        nextReview = now + 10_000L
        val quote = ProfitGatheringDemand.quote(bot, itemId, minimumUnitPrice = minimumUnitPrice,
            excluding = identity, marketOnly = marketOnly, gathered = if (marketOnly) gathered(bot) else 0)
        if (quote == null) {
            rejected = true
            release()
            return false
        }
        maximumProduced = minOf(maximumProduced, produced + quote.quantity)
        if (ProfitGatheringDemand.reserve(bot, this, minOf(quote.quantity, maximumProduced - produced))) return true
        rejected = true
        release()
        return false
    }

    /**
     * Releases prospective supply on pause, termination and completion; resume validates a new lease.
     */
    fun release() {
        nextReview = 0L
        ProfitGatheringDemand.release(identity)
    }

    /**
     * Offers only real transferable surplus through the existing native sale path.
     */
    fun completed(bot: Bot) {
        if (marketOnly) {
            observe(bot)
            val amount = minOf(gathered(bot), BotTradeManager.saleableAmount(bot, itemId))
            if (!BotTradeManager.hasNativeSaleStock(bot, itemId, amount)) return
            val saleDeadline = System.currentTimeMillis() + 300_000L
            val sale = NativeProductionSaleScript.createForOutput(bot, identity, itemId, amount,
                minimumUnitPrice, saleDeadline) ?: return
            bot.scriptStack.softPushHead(sale)
            return
        }
        if (!BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return
        val quote = BotTradeManager.computeTargetedSaleItem(bot, BotTradePrice.computePriceStrategy(bot), itemId)
            ?: return
        if (quote.totalPrice().toLong() < BotTradeManager.MINIMUM_OFFER_VALUE) return
        bot.scriptStack.softPushHead(TradeBotScript(bot, BotTradePurpose.SELL,
            BotMarketplace.select(bot), saleTarget = itemId))
    }

    private fun gathered(bot: Bot): Int =
        minOf(produced.toLong(), (physical(bot, itemId) - openingOutputStock).coerceAtLeast(0L)).toInt()

    fun save(): JsonObject = JsonObject().also { data ->
        data.addProperty("identity", identity)
        data.addProperty("item", itemId)
        data.add("outputs", JsonArray().also { array -> outputs.forEach(array::add) })
        data.addProperty("maximumProduced", maximumProduced)
        data.addProperty("produced", produced)
        data.addProperty("lastPhysicalStock", lastPhysicalStock)
        data.addProperty("deadline", deadline)
        data.addProperty("minimumPrice", minimumUnitPrice)
        data.addProperty("rejected", rejected)
        if (marketOnly) {
            data.addProperty("marketOnly", true)
            data.addProperty("openingOutputStock", openingOutputStock)
        }
    }

    companion object {
        /**
         * Bounds one dispatch without reserving any unchosen candidate.
         */
        internal fun create(bot: Bot, quote: ProfitGatheringDemand.Quote, duration: Duration,
                   outputs: Set<Int> = setOf(quote.itemId)): ProfitGatheringGoal {
            val amount = minOf(56, quote.quantity)
            return ProfitGatheringGoal(UUID.randomUUID().toString(), quote.itemId, outputs,
                amount, outputs.sumOf { physical(bot, it) },
                System.currentTimeMillis() + duration.inWholeMilliseconds.coerceAtLeast(1L),
                quote.minimumUnitPrice, marketOnly = quote.marketOnly,
                openingOutputStock = if (quote.marketOnly) physical(bot, quote.itemId) else 0L)
                .also { if (!ProfitGatheringDemand.reserve(bot, it, amount)) it.rejected = true }
        }

        @JvmStatic
        fun load(data: JsonObject): ProfitGatheringGoal = ProfitGatheringGoal(
            data.get("identity").asString, data.get("item").asInt,
            data.getAsJsonArray("outputs").map { it.asInt }.toSet(),
            data.get("maximumProduced").asInt, data.get("lastPhysicalStock").asLong,
            data.get("deadline").asLong, data.get("minimumPrice").asLong,
            data.get("produced").asInt,
            data.get("marketOnly")?.asBoolean ?: false,
            data.get("openingOutputStock")?.asLong ?: 0L).also {
                it.rejected = data.get("rejected")?.asBoolean ?: false
            }

        /**
         * Counts ordinary native stock and its notes without inventing ownership from a guide price.
         */
        fun physical(bot: Bot, id: Int): Long {
            fun count(item: Int) = bot.inventory.computeNonDynamicAmountForId(item).toLong() +
                bot.bank.computeNonDynamicAmountForId(item) + bot.equipment.computeNonDynamicAmountForId(item)
            val note = ItemDefinition.ALL.get(id).map { it.notedId.orElse(id) }.orElse(id)
            return count(id) + if (note == id) 0L else count(note)
        }
    }
}
