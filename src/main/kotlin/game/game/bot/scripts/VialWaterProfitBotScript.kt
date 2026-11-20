package game.bot.scripts

import api.bot.action.trading.BotMarketCooldown
import api.bot.action.trading.BotMarketExperience
import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotTradeIntentions
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.BotTradePrice
import api.bot.action.trading.BotTradePurpose
import api.bot.Suspendable.delay
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.script.ProductionSaleGuard
import api.predef.ext.get
import api.predef.ext.totalItemAmount
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.trading.TradeBotScript
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonality
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.floor
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds

/**
 * Banks profitable shop loads of empty vials and fills them through the native water action.
 * A targeted marketplace visit advertises surplus and preserves existing personal reserves.
 *
 * @author lare96
 */
class VialWaterProfitBotScript private constructor(
    bot: Bot,
    private var stage: Stage,
    private val initialFilled: Int,
    private val deadline: Long,
    private var minimumSalePrice: Long,
    private val batchSize: Int
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
        const val EMPTY_VIAL = 229
        const val EMPTY_VIAL_NOTE = 230
        const val WATER_VIAL = 227
        private const val SMALL_BATCH = 250
        private const val MEDIUM_BATCH = 500
        private const val LARGE_BATCH = 750
        private const val MAX_SHOP_PURCHASE = 100
        private const val LOOSE_LOAD = 27
        private const val ACQUISITION_MINUTES = 15

        internal fun batchFor(personality: BotPersonality): Int = when {
            personality.isClumsy -> SMALL_BATCH
            personality.isDextrous -> LARGE_BATCH
            else -> MEDIUM_BATCH
        }

        /**
         * Saved purchase and conversion stage. A resumed acquisition does not buy again; it uses any acquired
         * empties already present in the bank or inventory.
         *
         * @author lare96
         */
        class VialProfitData : BotScriptData() {
            var stage = Stage.ACQUIRE.name
            var initialFilled = 0
            var deadline = 0L
            var minimumSalePrice = 1L
            var batchSize = SMALL_BATCH

            override fun load(data: JsonObject) {
                stage = data.get("stage")?.asString ?: Stage.ACQUIRE.name
                initialFilled = data.get("initialFilled")?.asInt ?: 0
                deadline = data.get("deadline")?.asLong ?: 0L
                minimumSalePrice = data.get("minimumSalePrice")?.asLong ?: 1L
                batchSize = data.get("batchSize")?.asInt?.takeIf {
                    it == SMALL_BATCH || it == MEDIUM_BATCH || it == LARGE_BATCH
                } ?: SMALL_BATCH
            }

            override fun save(data: JsonObject) {
                data.addProperty("stage", stage)
                data.addProperty("initialFilled", initialFilled)
                data.addProperty("deadline", deadline)
                data.addProperty("minimumSalePrice", minimumSalePrice)
                data.addProperty("batchSize", batchSize)
            }
        }

        /**
         * A live shop quote used only until travel and price revalidation.
         *
         * @author lare96
         */
        private data class Purchase(val shop: Shop, val id: Int, val amount: Int, val cost: Int)

        private fun savedStage(value: String): Stage = Stage.entries.firstOrNull { it.name == value } ?: Stage.SELL

        private fun ownedEmpties(bot: Bot): Int =
            (bot.bank.computeAmountForId(EMPTY_VIAL).toLong() + bot.inventory.computeAmountForId(EMPTY_VIAL) +
                bot.inventory.computeAmountForId(EMPTY_VIAL_NOTE))
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        /**
         * Checks real ownership, stock, shop quotes, coin funding and the water-vial sale reserve before dispatch.
         */
        fun eligible(bot: Bot): Boolean {
            if (bot.state != EntityState.ACTIVE || !bot.isAlive || bot.inWilderness() ||
                !ActionSafety.NON_COMBAT.allows(bot) || !BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return false
            if (canAdvertise(bot)) return true
            val filledGuide = bot.world.economy.getPrice(WATER_VIAL)
            val emptyGuide = bot.world.economy.getPrice(EMPTY_VIAL)
            if (filledGuide <= 0 || saleUnit(bot, filledGuide) <= emptyGuide) return false
            val filled = bot.totalItemAmount(WATER_VIAL).toLong()
            val batchSize = batchFor(bot.personality)
            if (filled > Int.MAX_VALUE - batchSize) return false
            val minimum = minimumBatch(bot, saleUnit(bot, filledGuide))
            if (minimum !in 1..batchSize) return false
            if (ownedEmpties(bot) >= minimum) return true
            val wallet = bot.totalItemAmount(Currency.COINS.id)
            val unit = saleUnit(bot, filledGuide)
            return shopQuotes(bot, unit, batchSize, wallet).any()
        }

        private fun saleUnit(bot: Bot, filledGuide: Long): Long {
            val maximumDiscount = BotTradePrice.computePriceStrategy(bot).sell.endInclusive
            val lowestStrategyPrice = (filledGuide - floor(filledGuide * maximumDiscount).toLong())
                .coerceAtLeast(1L)
            return BotMarketExperience.sell(bot, WATER_VIAL, lowestStrategyPrice)
        }

        private fun minimumBatch(bot: Bot, unit: Long): Int {
            val minimumSale = (BotTradeManager.MINIMUM_OFFER_VALUE + unit - 1L) / unit
            val retained = BotTradeIntentions.retained(bot, WATER_VIAL).toLong()
            val filled = bot.totalItemAmount(WATER_VIAL).toLong()
            return (retained + minimumSale - filled).coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }

        private fun canAdvertise(bot: Bot): Boolean =
            BotTradeManager.hasNativeSaleStock(bot, WATER_VIAL, BotTradeManager.saleableAmount(bot, WATER_VIAL))

        private fun shopQuotes(bot: Bot, unit: Long, remaining: Int, wallet: Int): Sequence<Purchase> =
            bot.world.shops.values().asSequence()
                .filter { it.currency == Currency.COINS && it.npcSellers.isNotEmpty() }
                .flatMap { shop ->
                    sequenceOf(EMPTY_VIAL_NOTE, EMPTY_VIAL).mapNotNull { id ->
                        val limit = if (id == EMPTY_VIAL_NOTE) MAX_SHOP_PURCHASE else LOOSE_LOAD
                        quote(shop, id, unit, minOf(remaining, limit), wallet)
                    }
                }

        private fun fillMinutes(batchSize: Int) = (batchSize / 10).minutes

        private fun quote(shop: Shop, id: Int, saleUnit: Long, maximum: Int, wallet: Int): Purchase? {
            if (wallet < 1 || id !in shop.itemIds) return null
            val stock = minOf(maximum, shop.items.computeAmountForId(id))
            return (stock downTo 1).firstNotNullOfOrNull { amount ->
                val cost = shop.computeBuyValue(id, amount).orElse(-1)
                if (cost in 1..wallet && minimumAsk(cost, amount) <= saleUnit)
                    Purchase(shop, id, amount, cost) else null
            }
        }

        private fun minimumAsk(cost: Int, amount: Int): Long =
            ProductionSaleGuard.minimumGross((cost.toLong() + amount - 1) / amount, 1, taxed = false)
    }

    constructor(bot: Bot) : this(bot, batchFor(bot.personality))

    private constructor(bot: Bot, batchSize: Int) : this(bot, Stage.ACQUIRE, bot.totalItemAmount(WATER_VIAL),
        System.currentTimeMillis() + ACQUISITION_MINUTES.minutes.inWholeMilliseconds +
            fillMinutes(batchSize).inWholeMilliseconds, 1L, batchSize)

    constructor(bot: Bot, data: VialProfitData) : this(bot,
        savedStage(data.stage), data.initialFilled, data.deadline, data.minimumSalePrice, data.batchSize)

    private fun active(): Boolean = System.currentTimeMillis() < deadline && isActionSafe() &&
        bot.state == EntityState.ACTIVE && !bot.inWilderness()

    override suspend fun run(): Boolean {
        if (stage == Stage.FILLING) {
            stage = Stage.SELL
            return true
        }
        if (!active()) return true
        if (stage == Stage.ACQUIRE) {
            if (canAdvertise(bot)) {
                stage = Stage.SELL
                return true
            }
            // Commit the attempt before any travel or shop operation can suspend.
            stage = Stage.ACQUIRING
            val before = ownedEmpties(bot)
            if (before < batchSize) {
                withTimeoutOrNull(minOf(ACQUISITION_MINUTES.minutes.inWholeMilliseconds,
                        deadline - System.currentTimeMillis())) {
                    var failed = false
                    while (ownedEmpties(bot) < batchSize && active()) {
                        val bought = buyShopLoad()
                        if (!bought && failed) return@withTimeoutOrNull
                        failed = !bought
                    }
                }
                val bought = ownedEmpties(bot) - before
                if (bought > 0) bot.log("Stocked $bought empty vials for filling.")
            }
            stage = Stage.FILL
        }
        if (stage == Stage.ACQUIRING) stage = Stage.FILL
        if (stage == Stage.FILL) {
            val guide = bot.world.economy.getPrice(WATER_VIAL)
            if (guide <= 0 || saleUnit(bot, guide) <= bot.world.economy.getPrice(EMPTY_VIAL)) return true
            if (bot.inventory.computeAmountForId(EMPTY_VIAL_NOTE) > 0 &&
                (!handler.banking.travelToBankOpen() || !handler.banking.depositInventory() || !active())) return true
            val amount = minOf(batchSize, ownedEmpties(bot))
            val unnotedFilled = bot.bank.computeAmountForId(WATER_VIAL).toLong() +
                bot.inventory.computeAmountForId(WATER_VIAL)
            if (amount == 0 ||
                unnotedFilled + amount > Int.MAX_VALUE) return true
            bot.log("Filling up to $amount vials for the market.")
            val target = (unnotedFilled + amount).toInt()
            stage = Stage.FILLING
            bot.scriptStack.pushHead(FillWaterBotScript(bot, EMPTY_VIAL, target, fillMinutes(batchSize),
                goalDeadline = deadline))
            return false
        }
        return true
    }

    private suspend fun buyShopLoad(): Boolean {
        val guide = bot.world.economy.getPrice(WATER_VIAL)
        if (guide <= 0 || !active()) return false
        val profitablePrice = saleUnit(bot, guide)
        if (profitablePrice <= bot.world.economy.getPrice(EMPTY_VIAL)) return false
        if (!handler.banking.travelToBankOpen() || !active()) return false
        if (previousBankMode == null) previousBankMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        handler.banking.clickBankingMode(false)
        if (!handler.banking.depositInventory(setOf(Currency.COINS.id)) || !active()) return false
        val remaining = batchSize - ownedEmpties(bot)
        val wallet = bot.totalItemAmount(Currency.COINS.id)
        val initial = shopQuotes(bot, profitablePrice, remaining, wallet).mapNotNull { purchase ->
            handler.shop.shopDistance(purchase.shop)?.let { purchase to it }
        }.minWithOrNull(compareByDescending<Pair<Purchase, Long>> { it.first.amount }
            .thenBy { it.second }.thenBy { it.first.cost })?.first ?: return false
        val shop = initial.shop
        val id = initial.id
        val planned = if (id == EMPTY_VIAL_NOTE) minOf(remaining, shop.items.computeAmountForId(id)) else initial.amount
        val rounds = (planned + MAX_SHOP_PURCHASE - 1) / MAX_SHOP_PURCHASE
        val budget = if (id == EMPTY_VIAL_NOTE) {
            val quote = shop.computeBuyValue(id, planned).orElse(initial.cost)
            minOf(wallet.toLong(), quote.toLong() + rounds).toInt()
        } else initial.cost
        val missing = (budget - bot.inventory.computeAmountForId(Currency.COINS.id)).coerceAtLeast(0)
        if (missing > 0 && !handler.banking.withdraw(Item(Currency.COINS.id, missing))) return false
        ownOverlay = handler.shop.travelToShop(shop, ::active) ?: return false
        var bought = 0
        var spendingLeft = budget
        while (bought < planned && active()) {
            val guideNow = bot.world.economy.getPrice(WATER_VIAL)
            if (guideNow <= 0) break
            val capacity = if (id == EMPTY_VIAL_NOTE) MAX_SHOP_PURCHASE else bot.inventory.computeRemainingSize()
            val current = quote(shop, id, saleUnit(bot, guideNow), minOf(planned - bought, capacity),
                minOf(spendingLeft, bot.inventory.computeAmountForId(Currency.COINS.id))) ?: break
            if (!handler.shop.buyQuoted(shop, id, current.amount, current.cost)) break
            bought += current.amount
            spendingLeft -= current.cost
            minimumSalePrice = maxOf(minimumSalePrice, minimumAsk(current.cost, current.amount))
            if (bought < planned) delay(600.milliseconds)
        }
        return bought > 0
    }

    override suspend fun finish() {
        if (ownOverlay != null && bot.overlays[ShopInterface::class] === ownOverlay) bot.overlays.closeWindows()
        previousBankMode?.let { bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, it) }
        previousBankMode = null
        ownOverlay = null
    }

    override suspend fun completed() {
        if (stage != Stage.SELL || bot.state != EntityState.ACTIVE || !ActionSafety.NON_COMBAT.allows(bot)) return
        val produced = bot.totalItemAmount(WATER_VIAL) - initialFilled
        if (produced > 0) {
            val surplus = BotTradeManager.saleableAmount(bot, WATER_VIAL)
            bot.log("Filled $produced vials. Saleable surplus: $surplus.")
        }
        if (!BotMarketCooldown.ready(bot, BotTradePurpose.SELL)) return
        val quote = BotTradeManager.computeTargetedSaleItem(bot,
            BotTradePrice.computePriceStrategy(bot), WATER_VIAL) ?: return
        if (quote.price < minimumSalePrice || quote.totalPrice().toLong() < BotTradeManager.MINIMUM_OFFER_VALUE) return
        bot.scriptStack.softPushHead(TradeBotScript(bot, BotTradePurpose.SELL,
            BotMarketplace.select(bot), saleTarget = WATER_VIAL))
    }

    override fun snapshot() = VialProfitData().also {
        it.stage = stage.name
        it.initialFilled = initialFilled
        it.deadline = deadline
        it.minimumSalePrice = minimumSalePrice
        it.batchSize = batchSize
    }
}
