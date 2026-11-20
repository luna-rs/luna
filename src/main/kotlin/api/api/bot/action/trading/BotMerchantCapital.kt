package api.bot.action.trading

import api.attr.Attr
import api.bot.isRealBot
import api.predef.ext.get
import api.predef.ext.totalItemAmount
import com.google.common.math.LongMath
import engine.bot.gear.BotItemTracker.Companion.itemOwnershipRevision
import engine.trade.ConfirmTradeInterface
import engine.trade.OfferTradeInterface
import game.bot.scripts.trading.TradeBotScript
import game.player.item.consume.potion.PotionFamily
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Shares merchant cash and projected resale exposure across native trading, shops and market visits.
 * Holdings are derived from native containers and existing owner offers; this creates no financial ledger.
 *
 * @author lare96
 */
object BotMerchantCapital {

    /**
     * One existing owner order's actual stock, remaining bid commitment and collectable coins.
     *
     * @author lare96
     */
    internal data class Position(val id: Int, val stock: Long, val bidQuantity: Long,
                                 val bidPrice: Long, val collectableCoins: Long)

    /**
     * Ordinary physical ownership cached only until a native container quantity changes.
     *
     * @author lare96
     */
    private class Ownership {
        var revision = Long.MIN_VALUE
        var physical = emptyMap<Int, Long>()
        var equipped = emptyMap<Int, Long>()
    }

    private val ownership = Attr.obj { Ownership() }

    fun active(bot: Bot): Boolean = bot.isRealBot() && !bot.isConnectedPlayer && !bot.isTemporary && bot.preferences.isMerchant

    /**
     * Allocates exact personal targets and shared food/dose reserves once across actual and promised goods.
     *
     * @author lare96
     */
    internal class Protection(val exact: MutableMap<Int, Long>, var food: Long,
                             val potions: MutableMap<PotionFamily, Long>, val foodIds: Set<Int>) {
        fun copy(): Protection = Protection(exact.toMutableMap(), food, potions.toMutableMap(), foodIds)

        fun take(items: Map<Int, Long>): Map<Int, Long> {
            val result = HashMap<Int, Long>()
            for ((id, amount) in items) {
                val count = minOf(amount, exact[id] ?: 0L).coerceAtLeast(0L)
                if (count > 0) { result[id] = count; exact[id] = exact.getValue(id) - count }
            }
            for ((id, amount) in items.toSortedMap()) {
                val remaining = (amount - (result[id] ?: 0L)).coerceAtLeast(0L)
                if (remaining == 0L) continue
                val family = PotionFamily.forId(id)
                val count = when {
                    id in foodIds -> minOf(remaining, food).also { food -= it }
                    family != null && (potions[family] ?: 0L) > 0L -> {
                        val doses = family.doses(id).toLong()
                        val needed = potions.getValue(family)
                        minOf(remaining, needed / doses + if (needed % doses == 0L) 0L else 1L).also {
                            potions[family] = (needed - it * doses).coerceAtLeast(0L)
                        }
                    }
                    else -> 0L
                }
                if (count > 0) result[id] = (result[id] ?: 0L) + count
            }
            return result
        }
    }

    /**
     * One bounded decision snapshot. Guide marks measure risk; only physical coins can fund a transfer.
     *
     * @author lare96
     */
    class Balance internal constructor(private val bot: Bot, val coins: Long, val reservedCoins: Long, val lockedCoins: Long,
                                       val collectableCoins: Long, val exposure: Long,
                                       val stockExposure: Long, val busy: Boolean,
                                       private val stock: Map<Int, Long>, private val spare: Protection,
                                       private val requiredCoins: Long, private val fraction: Double,
                                       private val prices: MutableMap<Int, Long>) {
        private val capital = add(add(coins, collectableCoins), exposure)
        val electiveCoins: Long get() = if (busy) 0L else minOf((coins - reservedCoins).coerceAtLeast(0L),
            (capital / 4L - exposure).coerceAtLeast(0L))
        val stockHeavy: Boolean get() = exposure > 0L && electiveCoins == 0L
        val stockingCoins: Long get() = if (!bot.preferences.isMerchant || busy ||
            coins < BotTradeManager.MINIMUM_OFFER_VALUE) 0L
            else minOf(coins / 20L, (coins - reservedCoins).coerceAtLeast(0L))
        val cashRecovery: Boolean get() = coins < BotTradeManager.MINIMUM_OFFER_VALUE ||
            coins < maxOf(requiredCoins, (capital.toDouble() * (1.0 - fraction)).toLong())

        fun personalDeficit(id: Int): Long = spare.copy().take(mapOf(base(id) to Int.MAX_VALUE.toLong()))[base(id)] ?: 0L

        fun hasPersonalDemand(): Boolean = spare.food > 0 || spare.potions.values.any { it > 0 } ||
            spare.exact.values.any { it > 0 }

        /**
         * Checks a real batch cost, including the different guide mark of discounted purchases.
         */
        fun permits(id: Int, quantity: Int, cost: Long, personal: Boolean = true): Boolean {
            return permitsQuantity(id, quantity, cost, if (personal) personalDeficit(id) else 0L, personal)
        }

        /**
         * Allows a small cash-funded restock of useful default stock when guide marks constrain speculative capital.
         */
        fun permitsShopPurchase(id: Int, quantity: Int, cost: Long): Boolean =
            quantity > 0 && cost > 0 && (BotShopResale.target(id) > 0 && cost <= stockingCoins ||
                !cashRecovery && permits(id, quantity, cost, personal = false))

        private fun permitsQuantity(id: Int, quantity: Int, cost: Long, personalLimit: Long, personal: Boolean): Boolean {
            if (busy || quantity < 1 || cost < 0L || cost > coins) return false
            val protected = minOf(quantity.toLong(), personalLimit)
            val added = mark(id, quantity - protected)
            if (added == 0L && personal) return true
            return projected(coins - cost, add(exposure, added))
        }

        /**
         * Binary-searches only scalar quantity/cost arithmetic, never ownership, shops or the world catalogue.
         */
        fun quantity(id: Int, requested: Int, unitPrice: Long, personal: Boolean = true): Int {
            if (requested < 1 || unitPrice < 1 || busy) return 0
            var low = 1L
            var high = minOf(requested.toLong(), coins / unitPrice, Int.MAX_VALUE.toLong() / unitPrice)
            var result = 0
            val personalLimit = if (personal) personalDeficit(id) else 0L
            while (low <= high) {
                val middle = (low + high) ushr 1
                if (permitsQuantity(id, middle.toInt(), middle * unitPrice, personalLimit, personal)) {
                    result = middle.toInt(); low = middle + 1
                } else high = middle - 1
            }
            return result
        }

        private fun projected(afterCoins: Long, afterExposure: Long): Boolean =
            afterCoins >= reservedCoins && afterExposure <= add(add(afterCoins, collectableCoins), afterExposure) / 4L

        private fun mark(id: Int, quantity: Long): Long = multiply(quantity,
            prices.getOrPut(base(id)) { BotMerchantValuation.peekGuide(base(id)).coerceAtLeast(1L) })

        internal fun allows(outgoing: Map<Int, Long>, incoming: Map<Int, Long>): Boolean {
            val paid = (outgoing[995] ?: 0L) - (incoming[995] ?: 0L)
            val afterCoins = coins - paid
            if (afterCoins < 0L) return false
            val goods = incoming.filterKeys { it != 995 }
            if (busy && goods.isNotEmpty()) return false
            val protected = spare.copy().take(goods)
            val added = goods.entries.fold(0L) { total, (id, amount) ->
                add(total, mark(id, (amount - (protected[id] ?: 0L)).coerceAtLeast(0L)))
            }
            if (added == 0L) return true
            if (outgoing.keys.none { it != 995 } && protected.values.none { it > 0L }) {
                val forecast = goods.entries.fold(0L) { total, (id, amount) ->
                    add(total, multiply(amount, BotMerchantProceeds.forecastCeiling(bot, id,
                        amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())))
                }
                if (paid > forecast) {
                    val ceiling = goods.entries.fold(0L) { total, (id, amount) ->
                        add(total, multiply(amount, BotMerchantProceeds.ceiling(bot, id,
                            amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), Long.MAX_VALUE)))
                    }
                    if (paid > ceiling) return false
                }
            }
            val released = outgoing.entries.filter { it.key != 995 }.fold(0L) { total, (id, amount) ->
                add(total, mark(id, minOf(amount, stock[id] ?: 0L)))
            }
            val afterExposure = add((exposure - released).coerceAtLeast(0L), added)
            if (afterExposure <= exposure && afterCoins >= coins) return true
            return projected(afterCoins, afterExposure)
        }
    }

    /**
     * Captures fresh reserves and owner orders while reusing revision-indexed ordinary physical ownership.
     * Offered goods are restored logically because native trade temporarily removes them from inventory.
     */
    fun snapshot(bot: Bot, offered: Collection<Item>? = null): Balance {
        val cached = bot.attributes()[ownership.attr]
        if (cached.revision != bot.itemOwnershipRevision) {
            cached.physical = quantities((bot.inventory.filterNotNull() + bot.bank.filterNotNull()).filter { !it.isDynamic })
            cached.equipped = quantities(bot.equipment.filterNotNull().filter { !it.isDynamic })
            cached.revision = bot.itemOwnershipRevision
        }
        val physical = cached.physical.toMutableMap()
        val nativeOffer = offered ?: (bot.overlays[OfferTradeInterface::class]?.items
            ?: bot.overlays[ConfirmTradeInterface::class]?.takeUnless { it.completed }?.offer?.items)
            ?.filterNotNull().orEmpty()
        for ((id, amount) in quantities(nativeOffer)) physical[id] = add(physical[id] ?: 0L, amount)
        val coins = physical.remove(995) ?: 0L
        val positions = BotMerchantMarket.positions(bot)
        val tangible = physical.toMutableMap()
        for ((id, amount) in cached.equipped) tangible[id] = add(tangible[id] ?: 0L, amount)
        for (position in positions) tangible[position.id] = add(tangible[position.id] ?: 0L, position.stock)
        val wanted = BotMerchantMarket.personalTargets(bot)
        val targets = HashMap<Int, Long>()
        for ((original, amount) in BotTradeIntentions.retainedStock(bot)) {
            val id = base(original)
            targets[id] = maxOf(targets[id] ?: 0L, minOf(amount.toLong(), tangible[id] ?: 0L))
        }
        for (request in wanted) {
            val id = base(request.id())
            targets[id] = maxOf(targets[id] ?: 0L, request.target().toLong())
        }
        (bot.scriptStack.current() as? TradeBotScript)?.merchantPurchaseTarget()?.let { target ->
            val id = base(target.id)
            targets[id] = maxOf(targets[id] ?: 0L, target.amount.toLong())
        }
        val foodIds = BotFoodDemand.alternatives(bot)
        val families = BotPotionDemand.requestedPotionFamilies(bot).associateWith { BotPotionDemand.reserveDoses(bot, it).toLong() }
        for (id in targets.keys.toList()) {
            val family = PotionFamily.forId(id)
            if (id in foodIds || family != null && family in families) {
                targets[id] = BotTradeIntentions.literalSupplyTarget(bot, id).toLong()
            }
        }
        val protection = Protection(targets, BotFoodDemand.reserve(bot).toLong(), families.toMutableMap(), foodIds)
        val protectedEquipment = protection.take(cached.equipped)
        var locked = 0L
        var claims = 0L
        for (position in positions) {
            if (position.stock > 0) physical[position.id] = add(physical[position.id] ?: 0L, position.stock)
            locked = add(locked, multiply(position.bidQuantity, position.bidPrice))
            claims = add(claims, position.collectableCoins)
        }
        val protected = protection.take(physical)
        val surplus = physical.mapValuesTo(HashMap()) { (id, count) -> (count - (protected[id] ?: 0L)).coerceAtLeast(0L) }
        for ((id, count) in cached.equipped) {
            val extra = (count - (protectedEquipment[id] ?: 0L)).coerceAtLeast(0L)
            if (extra > 0L) surplus[id] = add(surplus[id] ?: 0L, extra)
        }
        val prices = HashMap<Int, Long>()
        fun risk(id: Int, count: Long): Long = multiply(count,
            prices.getOrPut(id) { BotMerchantValuation.peekGuide(id).coerceAtLeast(1L) })
        val stockExposure = surplus.entries.fold(0L) { total, (id, count) -> add(total, risk(id, count)) }
        var exposure = stockExposure
        for (position in positions) {
            val kept = protection.take(mapOf(position.id to position.bidQuantity))[position.id] ?: 0L
            exposure = add(exposure, multiply((position.bidQuantity - kept).coerceAtLeast(0L), position.bidPrice))
        }
        var required = 0L
        val production = HashSet<Any>()
        for (request in BotTradeIntentions.purchases(bot)) {
            val limit = BotTradeIntentions.productionLimit(bot, request.id())
            if (limit != null && production.add(limit.identity)) required = add(required, limit.maximumCoins)
            else if (limit == null && request.priority() == WantedItemPriority.URGENT) {
                required = add(required, risk(request.id(), (request.min().toLong() - bot.totalItemAmount(request.id())).coerceAtLeast(0L)))
            }
        }
        val fraction = BotTradeIntentions.budgetFraction(bot, BotTradePurpose.MERCHANT_BUY)
        val reserve = maxOf(required.coerceAtMost(coins), (coins.toDouble() * (1.0 - fraction)).toLong())
        return Balance(bot, coins, reserve, locked, claims, exposure, stockExposure, BotMerchantMarket.busy(bot),
            surplus, protection, required, fraction, prices)
    }

    /**
     * Keeps personal funding available while restricting a purely speculative prepared purse.
     */
    fun purse(bot: Bot, existing: Int, balance: Balance? = null): Int {
        if (!active(bot)) return existing
        val current = balance ?: snapshot(bot)
        return if (current.busy) 0 else if (current.hasPersonalDemand()) existing
        else minOf(existing.toLong(), current.electiveCoins).toInt()
    }

    /**
     * Rechecks net native transfers at both acceptance screens, including payment already on the offer.
     */
    fun allows(bot: Bot, offered: Collection<Item>, received: Collection<Item>): Boolean {
        if (!active(bot)) return true
        if (offered.any { it.isDynamic } || received.any { it.isDynamic }) return false
        val own = quantities(offered).toMutableMap()
        val other = quantities(received).toMutableMap()
        for (id in own.keys.intersect(other.keys)) {
            val returned = minOf(own.getValue(id), other.getValue(id))
            own[id] = own.getValue(id) - returned; other[id] = other.getValue(id) - returned
        }
        return snapshot(bot, offered).allows(own.filterValues { it > 0L }, other.filterValues { it > 0L })
    }

    /**
     * Revalidates a fixed-price standing purchase before native escrow begins. One order has one purpose.
     */
    fun allowsPurchase(bot: Bot, id: Int, quantity: Int, unitPrice: Int): Boolean {
        if (!active(bot)) return true
        if (quantity < 1 || unitPrice < 1) return false
        val current = snapshot(bot)
        val personal = current.personalDeficit(id)
        if (personal > 0L && quantity > personal) return false
        if (personal == 0L && BotMerchantProceeds.ceiling(bot, id, quantity, unitPrice.toLong()) < unitPrice) return false
        return current.permits(id, quantity, quantity.toLong() * unitPrice)
    }

    private fun quantities(items: Collection<Item>): Map<Int, Long> {
        val result = HashMap<Int, Long>()
        for (item in items) {
            val definition = ItemDefinition.ALL.get(item.id).orElse(null) ?: continue
            if (item.amount <= 0 || item.isDynamic || !definition.isTradeable) continue
            val id = definition.unnotedId.orElse(item.id)
            result[id] = add(result[id] ?: 0L, item.amount.toLong())
        }
        return result
    }

    private fun base(id: Int): Int = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)
    private fun add(a: Long, b: Long): Long = LongMath.saturatedAdd(a.coerceAtLeast(0L), b.coerceAtLeast(0L))
    private fun multiply(a: Long, b: Long): Long = LongMath.saturatedMultiply(a.coerceAtLeast(0L), b.coerceAtLeast(0L))
}
