package api.bot.action

import api.attr.Attr
import api.bot.isRealBot
import api.bot.action.trading.BotPotionDemand
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.predef.ext.totalItemAmount
import engine.controllers.Controllers.inWilderness
import game.player.item.consume.potion.Potion
import game.player.item.consume.potion.PotionFamily
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.item.shop.Currency
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Plans optional doses for one bank-to-bank outing and packs real bottles after essential supplies.
 *
 * @author lare96
 */
object BotPotionTrip {
    private const val SHOPPING_TIME = 60_000L
    private const val RETRY_DELAY = 300_000L
    private const val EMPTY_VIAL = 229
    private const val STOCK_RESERVE_TIME = 1_800_000L
    private val state = Attr.obj { Trip() }

    /**
     * Ordered alternatives for one use, with a dose target and a hard price ceiling.
     *
     * @author lare96
     */
    data class Need(val choices: List<Potion>, val doses: Int, val maxCoinsPerDose: Long,
                    val requires: Set<Potion> = emptySet())

    private class Trip {
        var owner: BotScript? = null
        var packed: Set<Potion> = emptySet()
        var retryAt = 0L
        var reserveUntil = 0L
        var reserves: Map<PotionFamily, Int> = emptyMap()
        val managed = mutableSetOf<Int>()
    }

    /**
     * One concrete, executable source for a single dose variant.
     *
     * @author lare96
     */
    internal data class Quote(val id: Int, val quantity: Int, val unitPrice: Long, val shop: Boolean)

    fun planned(bot: Bot): Set<Potion>? = bot.attributes()[state.attr].let {
        if (it.owner != null && it.owner === bot.scriptStack.current()) it.packed else null
    }

    /**
     * Keeps ordinary default targets from restarting a missed trip purchase.
     */
    fun manages(bot: Bot, family: PotionFamily): Boolean =
        !bot.preferences.isMerchant && family.fourDose in bot.attributes()[state.attr].managed

    /**
     * Retains already-owned doses for a recent personal outing without opening a shopping request.
     */
    fun protectedDoses(bot: Bot): Map<PotionFamily, Int> = bot.attributes()[state.attr].let {
        if (System.currentTimeMillis() < it.reserveUntil) it.reserves else emptyMap()
    }

    fun coins(bot: Bot): Long = bot.totalItemAmount(Currency.COINS.id).toLong()

    /**
     * Packs owned stock under the same slot and risk cap, even when there is no shopping budget.
     * False reports an interrupted owner or unsafe preparation. Missing optional stock permits departure.
     */
    suspend fun prepare(bot: Bot, needs: List<Need>, maximumCoins: Long, maximumSlots: Int,
                        allowed: () -> Boolean = { true }, shopUntil: Long = Long.MAX_VALUE,
                        canCarry: (List<Item>) -> Boolean = { true }): Boolean {
        val owner = bot.scriptStack.current() ?: return true
        val trip = bot.attributes()[state.attr]
        trip.owner = owner
        trip.packed = emptySet()
        trip.reserves = emptyMap()
        trip.managed += needs.flatMap { it.choices }.map { it.fourDose }

        fun safe() = allowed() && bot.scriptStack.current() === owner && !bot.client.isPendingLogout &&
            bot.state == EntityState.ACTIVE && !bot.inWilderness() && ActionSafety.NON_COMBAT.allows(bot)

        if (!safe()) return false
        if (!bot.bank.isOpen || maximumSlots <= 0 || needs.isEmpty()) return true

        val now = System.currentTimeMillis()
        val deadline = minOf(now + SHOPPING_TIME, shopUntil)
        val canShop = now >= trip.retryAt && maximumCoins > 0L && bot.isRealBot() && !bot.isTemporary
        fun shopping() = canShop && safe() && System.currentTimeMillis() < deadline
        var cash = minOf(maximumCoins, coins(bot), Int.MAX_VALUE.toLong()).coerceAtLeast(0L)
        val packed = mutableListOf<Item>()
        val selected = linkedSetOf<Potion>()
        val reserves = linkedMapOf<PotionFamily, Int>()
        val banking = bot.actionHandler.banking
        val initialCoins = bot.inventory.computeNonDynamicAmountForId(Currency.COINS.id)

        for (need in needs) {
            if (!safe()) return false
            if (!bot.bank.isOpen || packed.size >= maximumSlots) break
            val families = need.choices.filterNot { it in selected }.map(PotionFamily::forPotion)
            val remainingSlots = minOf(maximumSlots - packed.size, bot.inventory.capacity())
            val doseTarget = need.doses.coerceIn(0, minOf(12, remainingSlots * 4))
            if (doseTarget == 0 || families.isEmpty() || !selected.containsAll(need.requires)) continue

            // Try useful owned tiers before paying for a replacement. An inadmissible tier yields to its fallback.
            val stock = families.associateWith { BotPotionDemand.physicalDoses(bot, it) }
            val pending = if (shopping()) families.sumOf { BotPotionDemand.pendingDoses(bot, it) } else 0L
            var triedPurchase = false
            for (family in families.sortedBy { stock.getValue(it) <= 0L }) {
                if (!safe()) return false
                if (!bot.bank.isOpen) break
                val owned = stock.getValue(family)
                val potion = checkNotNull(family.potion)
                val missing = (doseTarget - owned - pending).coerceIn(0, doseTarget.toLong()).toInt()
                if (shopping() && !triedPurchase && missing > 0 && cash > 0L && need.maxCoinsPerDose > 0L) {
                    trip.retryAt = now + RETRY_DELAY
                    val supply = quote(bot, family, missing, cash, need.maxCoinsPerDose)
                    val expected = supply?.let { Item(family.idForDoses(minOf(4L, owned + family.doses(it.id)).toInt())) }
                    if (supply != null && expected != null && canCarry(packed + expected)) {
                        triedPurchase = true
                        val before = coins(bot)
                        withTimeoutOrNull((deadline - System.currentTimeMillis()).coerceAtLeast(1L)) {
                            if (supply.shop) buyFromShop(bot, supply, ::shopping)
                            else TripPotionSupply.buy(bot, supply, deadline, ::shopping)
                        }
                        if (!safe()) return false
                        cash = (cash - (before - coins(bot)).coerceAtLeast(0L)).coerceAtLeast(0L)
                        if (!bot.bank.isOpen && withTimeoutOrNull(5_000L) { banking.travelToBankOpen() } != true) break
                        val temporaryCoins = bot.inventory.computeNonDynamicAmountForId(Currency.COINS.id) - initialCoins
                        if (temporaryCoins > 0) banking.deposit(Item(Currency.COINS.id, temporaryCoins))
                    }
                }

                if (BotPotionDemand.physicalDoses(bot, family) <= 0L) continue
                val bottles = withTimeoutOrNull(10_000L) {
                    pack(bot, family, doseTarget, remainingSlots, ::safe) { canCarry(packed + it) }
                }.orEmpty()
                if (!safe()) return false
                if (bottles.isNotEmpty()) {
                    packed += bottles
                    selected += potion
                    reserves[family] = doseTarget
                    break
                }
            }
        }
        trip.packed = selected
        trip.reserves = reserves
        trip.reserveUntil = System.currentTimeMillis() + STOCK_RESERVE_TIME
        return safe()
    }

    private fun quote(bot: Bot, family: PotionFamily, missing: Int, cash: Long, ceiling: Long): Quote? {
        val capacity = bot.inventory.computeRemainingSize() - if (bot.inventory.contains(Currency.COINS.id)) 0 else 1
        if (capacity <= 0) return null
        val shops = family.ids.mapNotNull { id ->
            val doses = family.doses(id)
            val quantity = minOf(capacity, (missing + doses - 1) / doses)
            BotSupplySources.quote(bot, id, quantity)?.let { Quote(id, it.quantity, it.maximumUnitPrice, true) }
        }
        return selectQuote(family, missing, cash, capacity, ceiling, TripPotionSupply.quotes(bot, family) + shops)
    }

    internal fun selectQuote(family: PotionFamily, missing: Int, cash: Long, capacity: Int, ceiling: Long,
                             quotes: List<Quote>): Quote? {
        if (missing < 1 || cash < 1L || capacity < 1 || ceiling < 1L) return null
        return quotes.mapNotNull { offer ->
            val doses = family.doses(offer.id)
            if (doses < 1 || offer.unitPrice !in 1..ceiling.coerceAtMost(Int.MAX_VALUE.toLong() / 4L) * doses)
                return@mapNotNull null
            val quantity = minOf(capacity, (missing + doses - 1) / doses)
            val amount = minOf(quantity.toLong(), offer.quantity.toLong(), cash / offer.unitPrice).toInt()
            offer.copy(quantity = amount).takeIf { amount > 0 }
        }.minWithOrNull(compareBy<Quote> {
            (missing - it.quantity * family.doses(it.id)).coerceAtLeast(0)
        }.thenBy { if (it.id == family.threeDose) 0 else 1 }
            .thenBy { it.unitPrice * it.quantity }.thenByDescending { it.shop })
    }

    private suspend fun buyFromShop(bot: Bot, quote: Quote, allowed: () -> Boolean) {
        val handler = bot.actionHandler
        val coins = Currency.COINS.id
        val cost = Math.toIntExact(quote.quantity * quote.unitPrice)
        val missing = cost - bot.inventory.computeNonDynamicAmountForId(coins)
        val shop = bot.world.shops.sourcesForStock(quote.id).asSequence()
            .filter { it.currency == Currency.COINS && it.npcSellers.isNotEmpty() }
            .filter { it.items.computeAmountForId(it.stockIdFor(quote.id)) >= quote.quantity }
            .map { it to it.computeBuyValue(it.stockIdFor(quote.id), quote.quantity).orElse(-1) }
            .filter { it.second in 1..cost }.minByOrNull { it.second }?.first ?: return
        if (missing > 0 && !handler.banking.withdrawUnnoted(Item(coins, missing), allowed)) return
        if (allowed() && handler.shop.travelToShop(shop, allowed) != null && allowed()) {
            handler.shop.buyQuoted(shop, shop.stockIdFor(quote.id), quote.quantity, cost)
        }
        if (allowed()) bot.overlays.closeWindows()
    }

    private suspend fun pack(bot: Bot, family: PotionFamily, target: Int, slots: Int, safe: () -> Boolean,
                             canCarry: (List<Item>) -> Boolean): List<Item> {
        val banking = bot.actionHandler.banking
        if (!bot.bank.isOpen || !safe()) return emptyList()
        // Deposit purchased notes through native banking before selecting usable loose bottles.
        for (id in family.ids) {
            val note = Item(id).itemDef.notedId.orElse(-1)
            if (note >= 0 && bot.inventory.contains(note) && safe()) banking.depositAll(note)
        }
        val originalVials = bot.inventory.computeNonDynamicAmountForId(EMPTY_VIAL)
        val partials = family.ids.take(3)
        val partialDoses = partials.sumOf { id ->
            (bot.bank.computeNonDynamicAmountForId(id).toLong() + bot.inventory.computeNonDynamicAmountForId(id)) *
                family.doses(id)
        }
        val fullBottles = bot.bank.computeNonDynamicAmountForId(family.fourDose) +
            bot.inventory.computeNonDynamicAmountForId(family.fourDose).toLong()
        if (safe() && target > fullBottles * 4 && partialDoses >= 4 &&
            bot.bank.hasSpaceForAll((family.ids + EMPTY_VIAL).map { Item(it) })) {
            var loaded = partials.sumOf { bot.inventory.computeNonDynamicAmountForId(it) * family.doses(it) }
            for (id in partials.reversed()) {
                if (!safe() || !bot.bank.isOpen || loaded >= target + 2) break
                val doses = family.doses(id)
                val count = minOf(bot.inventory.computeRemainingSize(), bot.bank.computeNonDynamicAmountForId(id),
                    (target + 2 - loaded + doses - 1) / doses)
                if (count > 0 && banking.withdrawUnnoted(Item(id, count), safe)) loaded += count * doses
            }
            if (safe() && bot.inventory.count { it != null && it.id in partials && !it.isDynamic } >= 2) {
                bot.overlays.closeWindows()
                BotPotionDecanting.consolidateInventory(bot, family, safe)
                if (!safe() || !banking.travelToBankOpen()) return emptyList()
            }
        }

        // Normalize loose stock so the exact selected bottles pass the caller's cumulative risk check.
        if (!safe() || !bot.bank.isOpen) return emptyList()
        for (id in family.ids) {
            if (!safe()) return emptyList()
            if (bot.inventory.contains(id)) banking.depositAll(id)
        }
        val newVials = bot.inventory.computeNonDynamicAmountForId(EMPTY_VIAL) - originalVials
        if (safe() && newVials > 0) banking.deposit(Item(EMPTY_VIAL, newVials))
        val result = mutableListOf<Item>()
        val failed = mutableSetOf<Int>()
        var doses = 0
        while (safe() && result.size < slots && doses < target) {
            val ids = family.ids.filterNot { it in failed }
            val order = ids.filter { family.doses(it) >= target - doses } + ids.reversed()
            val id = order.firstOrNull { available ->
                bot.bank.computeNonDynamicAmountForId(available) > 0 && canCarry(result + Item(available))
            } ?: break
            val bottle = Item(id)
            if (banking.withdrawUnnoted(bottle, safe)) {
                result += bottle
                doses += family.doses(id)
            } else failed += id
        }
        return result
    }
}
