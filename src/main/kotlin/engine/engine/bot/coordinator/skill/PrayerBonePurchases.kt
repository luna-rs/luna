package engine.bot.coordinator.skill

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.trading.BotMarketPreference
import api.bot.action.trading.BotTradeManager
import api.bot.action.trading.NativeOutputDemand
import game.bot.scripts.skills.PrayerBoneStock
import game.skill.prayer.Bone
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.bot.Bot

/**
 * Selects a finite purchase from stock currently offered to the bot.
 *
 * @author lare96
 */
internal object PrayerBonePurchases {
    const val MAX_STOCK = 500
    private const val BULK = 100
    private var Bot.prayerPurchaseRetryAt by Attr.long()
    private var Bot.prayerFailedSource by Attr.int { -1 }

    private fun sourceKey(id: Int, listed: Boolean) = id * 2 + if (listed) 1 else 0

    fun recordMiss(bot: Bot, id: Int, listed: Boolean) {
        bot.prayerFailedSource = sourceKey(id, listed)
        bot.prayerPurchaseRetryAt = System.currentTimeMillis() + 300_000L
    }

    /**
     * A confirmed price and depth for one executable source.
     *
     * @author lare96
     */
    data class Stock(val quantity: Int, val price: Long)

    /**
     * One affordable order. The ceiling also bounds a later native trade if the seller changes.
     *
     * @author lare96
     */
    data class Order(val bone: Bone, val quantity: Int, val coins: Long, val ceiling: Long,
                     val listed: Boolean) {
        val gpPerBone get() = coins.toDouble() / quantity
    }

    fun select(bot: Bot): Order? {
        val merchant = BotMarketPreference.profitSeeking(bot)
        val wallet = bot.inventory.computeNonDynamicAmountForId(995).toLong() +
            bot.bank.computeNonDynamicAmountForId(995)
        val budget = wallet / if (merchant && !bot.personality.isDextrous) 5 else 4
        if (budget < 1) return null
        val reserves = PrayerBoneStock.reserves(bot)
        val owned = Bone.entries.associateWith { PrayerBoneStock.owned(bot, it.id) }
        val dextrous = bot.personality.isDextrous
        val usable = owned.filter { (bone, count) -> count > (reserves[bone.id] ?: 0) }
        if (!dextrous && usable.any { (bone, count) ->
                count - (reserves[bone.id] ?: 0) >= bot.inventory.capacity()
            }) return null
        val traders = NativeOutputDemand.traders(bot).toList()
        val highestOwnedXp = if (dextrous) usable.keys.maxOfOrNull { it.exp } ?: 0.0 else 0.0
        val failed = if (bot.prayerPurchaseRetryAt > System.currentTimeMillis()) bot.prayerFailedSource else -1
        val orders = buildList {
            for (bone in Bone.entries) {
                if (bone.exp < highestOwnedXp) continue
                if (!ItemDefinition.ALL.get(bone.id).map { it.isTradeable }.orElse(false)) continue
                val count = owned.getValue(bone)
                if (count - (reserves[bone.id] ?: 0) >= bot.inventory.capacity()) continue
                val target = MAX_STOCK - count
                if (failed == sourceKey(bone.id, listed = false)) continue
                val offers = traders.mapNotNull { it.profitGatheringSupply(bone.id) }
                    .map { Stock(it.item.amount, it.price) }
                order(bone, offers, target, budget, listed = false)?.let(::add)
            }
        }
        return choose(orders, dextrous, merchant)
    }

    fun order(bone: Bone, stock: List<Stock>, target: Int, budget: Long, listed: Boolean): Order? {
        var quantity = 0
        var coins = 0L
        var ceiling = 0L
        for (row in stock.filter { it.price > 0 && it.quantity > 0 }.sortedBy { it.price }) {
            val affordable = if (listed) (budget - coins) / row.price else budget / row.price - quantity
            val amount = minOf((target - quantity).toLong(), row.quantity.toLong(), affordable).toInt()
            if (amount <= 0) break
            val cost = amount * row.price
            if (!listed && cost < BotTradeManager.MINIMUM_OFFER_VALUE) continue
            quantity += amount
            coins += cost
            ceiling = row.price
            if (quantity == target) break
        }
        if (quantity == 0) return null
        return Order(bone, quantity, if (listed) coins else quantity * ceiling, ceiling, listed)
    }

    fun choose(orders: List<Order>, dextrous: Boolean, merchant: Boolean): Order? {
        val tieBreak = compareByDescending<Order> { it.quantity }.thenBy { it.gpPerBone }
            .thenByDescending { it.listed }.thenBy { it.bone.id }
        if (dextrous) return orders.minWithOrNull(compareByDescending<Order> { it.bone.exp }.then(tieBreak))
        val plentiful = orders.filter { it.quantity >= BULK }
            .ifEmpty { orders.filter { it.quantity >= 28 } }.ifEmpty { orders.filter { it.quantity >= 8 } }
            .ifEmpty { orders }
        return plentiful.minWithOrNull(compareBy<Order> {
            if (merchant) it.gpPerBone else it.gpPerBone / it.bone.exp
        }.then(tieBreak))
    }
}
