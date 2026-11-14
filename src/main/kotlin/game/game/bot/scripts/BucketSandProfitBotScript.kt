package game.bot.scripts

import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.BotMarketExperience
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.zone.SubZone
import api.predef.ext.get
import api.predef.ext.totalItemAmount
import api.predef.world
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.floor
import kotlin.time.Duration.Companion.minutes

/**
 * Turns a small, owned or profitably purchased batch of empty buckets into buckets of sand without experience.
 * A native sand-filling child owns the conversion; a targeted marketplace visit advertises the resulting stock.
 * Each attempt purchases at most one batch and retains its stage through interruption or logout.
 *
 * @author lare96
 */
class BucketSandProfitBotScript private constructor(
    bot: Bot,
    private var stage: Stage,
    private val initialFilled: Int,
    private val deadline: Long
) : BotScript(bot) {
    private var ownOverlay: ShopInterface? = null
    private var previousBankMode: Int? = null

    /**
     * Durable one-way stages. ACQUIRING resumes at filling so interrupted purchases are never repeated.
     *
     * @author lare96
     */
    private enum class Stage { ACQUIRE, ACQUIRING, FILL, FILLING, SELL }

    /**
     * Shared eligibility and finite shop selection for one production batch.
     *
     * @author lare96
     */
    companion object {
        const val EMPTY_BUCKET = 1925
        const val SAND_BUCKET = 1783
        private const val MAX_BATCH = 25
        private const val MAX_UNSOLD = 25

        /**
         * Saved purchase and conversion stage. A resumed acquisition does not buy again; it uses any acquired
         * empties already present in the bank or inventory.
         *
         * @author lare96
         */
        class SandProfitData : BotScriptData() {
            var stage = Stage.ACQUIRE.name
            var initialFilled = 0
            var deadline = 0L

            override fun load(data: JsonObject) {
                stage = data.get("stage")?.asString ?: Stage.ACQUIRE.name
                initialFilled = data.get("initialFilled")?.asInt ?: 0
                deadline = data.get("deadline")?.asLong ?: 0L
            }

            override fun save(data: JsonObject) {
                data.addProperty("stage", stage)
                data.addProperty("initialFilled", initialFilled)
                data.addProperty("deadline", deadline)
            }
        }

        /**
         * A live shop quote used only until travel and price revalidation.
         *
         * @author lare96
         */
        private data class Purchase(val shop: Shop, val amount: Int, val cost: Int)

        private fun savedStage(value: String): Stage = Stage.entries.firstOrNull { it.name == value } ?: Stage.SELL

        private fun availableEmpties(bot: Bot): Int =
            (bot.bank.computeAmountForId(EMPTY_BUCKET).toLong() + bot.inventory.computeAmountForId(EMPTY_BUCKET) -
                BotTradeIntentions.retained(bot, EMPTY_BUCKET))
                .coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

        /**
         * Checks real ownership, stock, shop quotes, coin funding and the sand sale reserve before dispatch.
         */
        fun eligible(bot: Bot): Boolean {
            if (bot.state != EntityState.ACTIVE || !bot.isAlive || bot.inWilderness() ||
                !ActionSafety.NON_COMBAT.allows(bot) || !BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return false
            val filledGuide = world.economy.getPrice(SAND_BUCKET)
            val emptyGuide = world.economy.getPrice(EMPTY_BUCKET)
            if (filledGuide <= 0 || saleUnit(bot, filledGuide) <= emptyGuide) return false
            val filled = bot.totalItemAmount(SAND_BUCKET).toLong()
            val retained = BotTradeIntentions.retained(bot, SAND_BUCKET).toLong()
            if (filled - retained >= MAX_UNSOLD || filled > Int.MAX_VALUE - MAX_BATCH) return false
            val minimum = minimumBatch(bot, saleUnit(bot, filledGuide))
            if (minimum !in 1..MAX_BATCH) return false
            if (availableEmpties(bot) >= minimum) return true
            return findPurchase(bot, filledGuide, minimum) != null
        }

        private fun saleUnit(bot: Bot, filledGuide: Long): Long {
            val maximumDiscount = BotTradePrice.computePriceStrategy(bot).sell.endInclusive
            val lowestStrategyPrice = (filledGuide - floor(filledGuide * maximumDiscount).toLong())
                .coerceAtLeast(1L)
            return BotMarketExperience.sell(bot, SAND_BUCKET, lowestStrategyPrice)
        }

        private fun minimumBatch(bot: Bot, unit: Long): Int {
            val minimumSale = (BotTradeManager.MINIMUM_OFFER_VALUE + unit - 1L) / unit
            val retained = BotTradeIntentions.retained(bot, SAND_BUCKET).toLong()
            val filled = bot.totalItemAmount(SAND_BUCKET).toLong()
            return (retained + minimumSale - filled).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        private fun findPurchase(bot: Bot, filledGuide: Long, minimum: Int,
                                 wallet: Int = bot.totalItemAmount(Currency.COINS.id),
                                 only: Shop? = null): Purchase? {
            val saleUnit = saleUnit(bot, filledGuide)
            if (wallet < 1) return null
            val shops = if (only == null) world.shops.values() else listOf(only)
            return shops.asSequence().filter { shop ->
                shop.currency == Currency.COINS && EMPTY_BUCKET in shop.itemIds && shop.botAccess.apply(bot)
            }.mapNotNull { shop ->
                val stock = shop.items.computeAmountForId(EMPTY_BUCKET).coerceAtMost(MAX_BATCH)
                (stock downTo minimum).firstNotNullOfOrNull { amount ->
                    val cost = shop.computeBuyValue(EMPTY_BUCKET, amount).orElse(-1)
                    if (cost in 1..wallet && cost.toLong() < saleUnit * amount) Purchase(shop, amount, cost) else null
                }
            }.maxByOrNull { saleUnit * it.amount - it.cost }
        }
    }

    constructor(bot: Bot) : this(bot, Stage.ACQUIRE, bot.totalItemAmount(SAND_BUCKET),
        System.currentTimeMillis() + 30.minutes.inWholeMilliseconds)

    constructor(bot: Bot, data: SandProfitData) : this(bot,
        savedStage(data.stage), data.initialFilled, data.deadline)

    private fun active(): Boolean = System.currentTimeMillis() < deadline && isActionSafe() &&
        bot.state == EntityState.ACTIVE && !bot.inWilderness()

    override suspend fun run(): Boolean {
        if (stage == Stage.FILLING) {
            stage = Stage.SELL
            return true
        }
        if (!active()) return true
        if (stage == Stage.ACQUIRE) {
            // Commit the attempt before any travel or shop operation can suspend.
            stage = Stage.ACQUIRING
            if (availableEmpties(bot) <
                minimumBatch(bot, saleUnit(bot, world.economy.getPrice(SAND_BUCKET)))) {
                withTimeoutOrNull(minOf(5.minutes.inWholeMilliseconds, deadline - System.currentTimeMillis())) {
                    buyOneBatch()
                }
            }
            stage = Stage.FILL
        }
        if (stage == Stage.ACQUIRING) stage = Stage.FILL
        if (stage == Stage.FILL) {
            val guide = world.economy.getPrice(SAND_BUCKET)
            if (guide <= 0 || saleUnit(bot, guide) <= world.economy.getPrice(EMPTY_BUCKET)) return true
            val amount = minOf(MAX_BATCH, availableEmpties(bot))
            val unnotedFilled = bot.bank.computeAmountForId(SAND_BUCKET).toLong() +
                bot.inventory.computeAmountForId(SAND_BUCKET)
            if (amount < minimumBatch(bot, saleUnit(bot, guide)) ||
                unnotedFilled + amount > Int.MAX_VALUE) return true
            val target = (unnotedFilled + amount).toInt()
            stage = Stage.FILLING
            bot.scriptStack.pushHead(FillWaterBotScript(bot, EMPTY_BUCKET, target, 20.minutes,
                source = FillWaterBotScript.Source.SAND, goalDeadline = deadline))
            return false
        }
        return true
    }

    private suspend fun buyOneBatch(): Boolean {
        val guide = world.economy.getPrice(SAND_BUCKET)
        if (guide <= 0 || saleUnit(bot, guide) <= world.economy.getPrice(EMPTY_BUCKET)) return false
        val minimum = minimumBatch(bot, saleUnit(bot, guide))
        val initial = findPurchase(bot, guide, minimum) ?: return false
        if (!handler.banking.travelToBankOpen() || !active()) return false
        if (previousBankMode == null) previousBankMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        handler.banking.clickBankingMode(false)
        if (!handler.banking.depositInventory(setOf(Currency.COINS.id)) || !active()) return false
        val budget = minOf(bot.totalItemAmount(Currency.COINS.id).toLong(),
            guide * MAX_BATCH - 1L, Int.MAX_VALUE.toLong()).coerceAtLeast(0L).toInt()
        val missing = (budget - bot.inventory.computeAmountForId(Currency.COINS.id)).coerceAtLeast(0)
        if (missing > 0 && !handler.banking.withdraw(Item(Currency.COINS.id, missing))) return false
        bot.overlays.closeWindows()
        if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) return false
        if (!active() || bot.inventory.computeAmountForId(Currency.COINS.id) < 1) return false
        val current = findPurchase(bot, world.economy.getPrice(SAND_BUCKET), minimum,
            bot.inventory.computeAmountForId(Currency.COINS.id), initial.shop) ?: return false
        if (!bot.inventory.hasSpaceFor(Item(EMPTY_BUCKET, current.amount))) return false
        val overlay = ShopInterface(current.shop)
        bot.overlays.open(overlay)
        ownOverlay = overlay
        if (bot.overlays[ShopInterface::class] !== overlay || !active()) return false
        val quote = current.shop.computeBuyValue(EMPTY_BUCKET, current.amount).orElse(-1)
        val saleGuide = world.economy.getPrice(SAND_BUCKET)
        if (saleGuide <= 0 || saleUnit(bot, saleGuide) <= world.economy.getPrice(EMPTY_BUCKET) ||
            quote != current.cost ||
            quote.toLong() >= saleUnit(bot, saleGuide) * current.amount) return false
        return handler.shop.buyQuoted(current.shop, EMPTY_BUCKET, current.amount, quote)
    }

    override suspend fun finish() {
        if (ownOverlay != null && bot.overlays[ShopInterface::class] === ownOverlay) bot.overlays.closeWindows()
        previousBankMode?.let { bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, it) }
        previousBankMode = null
        ownOverlay = null
    }

    override suspend fun completed() {
        if (stage != Stage.SELL || bot.totalItemAmount(SAND_BUCKET) <= initialFilled ||
            bot.state != EntityState.ACTIVE || !ActionSafety.NON_COMBAT.allows(bot) ||
            !BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return
        val quote = BotTradeManager.computeTargetedSaleItem(bot,
            BotTradePrice.computePriceStrategy(bot), SAND_BUCKET) ?: return
        if (quote.totalPrice().toLong() < BotTradeManager.MINIMUM_OFFER_VALUE) return
        bot.scriptStack.softPushHead(TradeBotScript(bot, BotTradePurpose.SELL,
            BotMarketplace.select(bot), saleTarget = SAND_BUCKET))
    }

    override fun snapshot() = SandProfitData().also {
        it.stage = stage.name
        it.initialFilled = initialFilled
        it.deadline = deadline
    }
}
