package api.bot.action

import api.bot.script.BotScript.ActionSafety
import api.bot.action.trading.BotTradeIntentions
import api.predef.ext.get
import engine.controllers.Controllers.inWilderness
import game.item.degradable.jewellery.TeleportJewellery
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import game.skill.magic.Rune
import io.luna.game.model.EntityState
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.combat.Weapon
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Funds a concrete PK kit from existing gold, then uses ordinary wanted-item purchases.
 * Only dumb nonmerchant bots liquidate non-combat belongings for a shortfall. Missing shop stock never triggers sales.
 *
 * @author lare96
 */
internal class PkSupplyAcquisition(private val bot: Bot, private val handler: BotActionHandler) {
    private val selection = PkLoadoutSelection(bot)

    private val purchase = BotSupplyPurchase(bot, handler)

    suspend fun obtainFood(allowed: () -> Boolean): Boolean =
        FoodSupplyAcquisition(bot, handler).obtainForPatrol(allowed)

    /**
     * Prices a complete basket from real owned stock, shops and active sellers.
     */
    internal fun purchaseQuotes(): (Map<Int, Int>) -> Long? {
        val stock = (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() + bot.bank.filterNotNull())
            .groupingBy { it.unnotedItemDef.id() }.fold(0L) { count, item -> count + item.amount }
        val costs = mutableMapOf<Pair<Int, Int>, Long>()
        return { required ->
            basketCost(required) { id, target ->
                val missing = (target - (stock[id] ?: 0L)).coerceAtLeast(0L).toInt()
                if (missing == 0) 0L else costs.getOrPut(id to missing) { missingCost(id, missing) }
            }
        }
    }

    private fun quoteAvailable(required: Map<Int, Int>): Long? = basketCost(required) { id, target ->
        val missing = target - selection.owned(id)
        if (missing > 0) missingCost(id, missing) else 0L
    }

    private inline fun basketCost(required: Map<Int, Int>, cost: (Int, Int) -> Long): Long? {
        var total = 0L
        for ((id, target) in required) {
            val price = cost(id, target)
            if (price > Int.MAX_VALUE) return null
            total += price
        }
        return total.takeIf { it <= Int.MAX_VALUE }
    }

    private fun missingCost(id: Int, missing: Int): Long =
        minOf(shopCost(id, missing) ?: Long.MAX_VALUE, purchase.directQuote(id, missing)?.coins ?: Long.MAX_VALUE)

    /**
     * Combat equipment and consumables remain reserved even when they are absent from today's selected switches.
     * Notes are classified by their physical item, and dynamic items are never sold.
     */
    internal fun protectedItem(item: Item, reserved: Set<Int> = emptySet()): Boolean {
        val id = item.unnotedItemDef.id()
        if (item.isDynamic || !item.unnotedItemDef.isTradeable || id == 995 || id in reserved ||
            id in Rune.ID_TO_RUNE || id in Food.ID_TO_FOOD || BotPkPreference.isTeamCape(id) ||
            Potion.values().any { id in it.doses } ||
            TeleportJewellery.values().any { id in it.items || id == it.emptyId } || id in 8007..8015 || id == 4251 ||
            AmmoDefinition.ALL.values.any { id in it.ammo }) return true
        val weapon = WeaponDefinition.ALL.get(id).orElse(null)
        if (weapon != null && weapon.type !in setOf(Weapon.PICKAXE, Weapon.SICKLE)) return true
        val equipment = EquipmentDefinition.ALL.get(id).orElse(null) ?: return false
        return (0..10).any { equipment.getBonus(it) > 0 } && weapon == null
    }

    /**
     * Obtains a complete requested stock target. The whole funding/purchase visit has one five-minute deadline.
     * Failed or partial purchases remain owned, and departure still requires a fresh loadout/risk assessment.
     */
    suspend fun obtain(required: Map<Int, Int>, allowed: () -> Boolean = { true }): Boolean = withTimeoutOrNull(5.minutes) {
        if (!safe()) return@withTimeoutOrNull false
        BotTradeIntentions.recordSupplies(bot, required.map { (id, amount) -> Item(id, amount) })
        for ((id, amount) in required) bot.preferences.raiseWantedItemTarget(id, amount)
        try {
            val cost = quoteAvailable(required)
            if (cost != null && coins() < cost && !fund(cost, required.keys)) return@withTimeoutOrNull false
            val native = required.filter { (id, target) ->
                val missing = target - selection.owned(id)
                missing > 0 && shopCost(id, missing)?.let {
                    it <= (purchase.directQuote(id, missing)?.coins ?: Long.MAX_VALUE)
                } == true
            }
            val shopCost = quoteMissing(native)
            if (allowed() && safe() && shopCost != null && shopCost in 1L..minOf(coins(), Int.MAX_VALUE.toLong())) {
                handler.shop.obtainWantedItems(native, shopCost.toInt(), native.keys.associateWith { shopCost })
            }
            for ((id, amount) in required) {
                if (!allowed() || !safe()) return@withTimeoutOrNull false
                val missing = amount - selection.owned(id)
                if (missing <= 0) continue
                purchase.directQuote(id, missing)?.let { purchase.buy(it, allowed) }
                if (!allowed() || !safe()) return@withTimeoutOrNull false
                if (selection.owned(id) < amount && !handler.shop.obtainWantedItem(id, amount))
                    return@withTimeoutOrNull false
            }
            allowed() && safe() && required.all { (id, amount) -> selection.owned(id) >= amount }
        } finally {
            if (allowed()) bot.overlays.closeWindows()
        }
    } == true

    /**
     * Quotes only obtainable missing quantities, respecting stock shared between the candidate shops.
     * Null means that this visit cannot source a full kit, so selling belongings would be premature.
     */
    internal fun quoteMissing(required: Map<Int, Int>): Long? {
        var total = 0L
        for ((id, amount) in required) {
            val missing = amount - selection.owned(id)
            if (missing > 0) total += shopCost(id, missing) ?: return null
        }
        return total
    }

    private fun shopCost(id: Int, amount: Int): Long? {
        var missing = amount
        var total = 0L
        val shops = bot.world.shops.sourcesForStock(id).asSequence()
            .filter { it.currency == Currency.COINS && it.npcSellers.isNotEmpty() }
            .map { shop -> shop to shop.stockIdFor(id) }
            .sortedBy { (shop, stockId) -> shop.computeBuyValue(stockId, 1).orElse(Int.MAX_VALUE) }
        for ((shop, stockId) in shops) {
            val count = minOf(missing, shop.items.computeAmountForId(stockId))
            if (count < 1) continue
            val quote = shop.computeBuyValue(stockId, count).orElse(-1)
            if (quote < 0) continue
            total += quote.toLong() + count
            missing -= count
            if (missing == 0) return total
        }
        return null
    }

    /**
     * Sells only enough non-PK stock to cover this concrete funding target, with bounded actions and shop visits.
     * Wanted skilling stock may be sold as explicitly requested; PK supplies never become sale candidates.
     */
    internal suspend fun fund(target: Long, reserved: Set<Int>): Boolean {
        if (target < 0 || target > Int.MAX_VALUE || !safe()) return false
        if (coins() >= target) return true
        if (bot.preferences.isMerchant || !bot.personality.isDumb) return false
        if (!handler.banking.travelToBankOpen() || !safe() || !handler.banking.depositInventory() || !safe()) return false
        val candidates = bot.bank.filterNotNull().filter { !protectedItem(it, reserved) }
            .map { it.id }.distinct().sortedByDescending { id ->
                val price = shopsForSale(id).maxOfOrNull { it.computeSellValue(saleId(it, id), 1).coerceAtLeast(0) } ?: 0
                price.toLong() * bot.bank.computeAmountForId(id)
            }.take(16)
        var actions = 0
        for (id in candidates) {
            val failed = HashSet<Shop>()
            while (safe() && coins() < target && actions < 120) {
                val shop = shopsForSale(id).filter { it !in failed }.maxByOrNull { it.computeSellValue(saleId(it, id), 1) } ?: break
                if (!handler.banking.travelToBankOpen() || !safe() || !handler.banking.depositInventory() || !safe()) return false
                val saleId = saleId(shop, id)
                handler.banking.clickBankingMode(saleId != id)
                val quote = shop.computeSellValue(saleId, 1).coerceAtLeast(1)
                val needed = ((target - coins() + quote - 1) / quote).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                val capacity = if (Item(saleId).itemDef.isStackable) needed else 27
                val amount = minOf(needed, capacity, bot.bank.computeAmountForId(id))
                if (amount < 1 || !handler.banking.withdraw(Item(id, amount)) || !safe()) break
                if (handler.shop.travelToShop(shop, ::safe) == null) {
                    failed += shop
                    continue
                }
                var progressed = false
                while (safe() && coins() < target && actions < 120 &&
                    bot.overlays[ShopInterface::class]?.shop === shop && shop.computeCanSell(saleId)) {
                    val held = bot.inventory.computeAmountForId(saleId)
                    if (held < 1 || protectedItem(Item(id), reserved)) break
                    val unit = shop.computeSellValue(saleId, 1)
                    if (unit <= 0) break
                    val deficit = target - coins()
                    val batch = when {
                        shop.computeSellValue(saleId, minOf(100, held)).toLong() in 1L..deficit -> 100
                        held >= 10 && shop.computeSellValue(saleId, 10).toLong() in 1L..deficit -> 10
                        held >= 5 && shop.computeSellValue(saleId, 5).toLong() in 1L..deficit -> 5
                        else -> 1
                    }
                    val before = coins()
                    actions++
                    val success = when (batch) {
                        100 -> handler.shop.sell100(saleId)
                        10 -> handler.shop.sell10(saleId)
                        5 -> handler.shop.sell5(saleId)
                        else -> handler.shop.sell1(saleId)
                    }.await()
                    if (!success || coins() <= before || bot.inventory.computeAmountForId(saleId) >= held) break
                    progressed = true
                }
                if (coins() >= target) return true
                if (bot.inventory.computeAmountForId(saleId) > 0 || !progressed) failed += shop
                if (!progressed) break
            }
            if (coins() >= target) return true
        }
        return coins() >= target
    }

    private fun shopsForSale(id: Int): List<Shop> = bot.world.shops.values().filter {
        it.currency == Currency.COINS && it.npcSellers.isNotEmpty() && it.computeCanSell(saleId(it, id)) &&
            it.items.hasSpaceFor(Item(saleId(it, id))) && it.computeSellValue(saleId(it, id), 1) > 0
    }

    /**
     * Uses notes for general-store funding while preserving specialty-shop stock forms.
     */
    private fun saleId(shop: Shop, id: Int): Int {
        val definition = Item(id).itemDef
        return if (shop.buyPolicy == BuyPolicy.ALL && !definition.isStackable) definition.notedId.orElse(id) else id
    }

    private fun coins(): Long = bot.inventory.computeAmountForId(995).toLong() + bot.bank.computeAmountForId(995)

    private fun safe(): Boolean = bot.state == EntityState.ACTIVE && !bot.inWilderness() && ActionSafety.NON_COMBAT.allows(bot)
}
