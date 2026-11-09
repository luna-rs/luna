package api.bot.action

import api.attr.Attr
import api.bot.action.trading.BotStoreSales
import api.bot.action.trading.BotTradeIntentions
import api.bot.script.BotScript.ActionSafety
import api.bot.script.InventoryBotScript
import api.bot.skill.SkillingBotScript
import api.predef.ext.get
import api.predef.world
import io.luna.game.model.EntityState
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Recovers real bank-slot shortages with bounded native sales of completely removable low-value stacks.
 * Raw item transfers never invoke maintenance. Pending deposits and retained stock survive every suspension,
 * and moving goods into inventory is not counted as a successful recovery.
 *
 * @author lare96
 */
object BotBankSpace {

    const val START_FREE_SLOTS = 16
    const val TARGET_FREE_SLOTS = 32
    internal const val MAX_STACK_VALUE = 10_000L
    internal const val MAX_SESSION_VALUE = 50_000L
    private const val SESSION_MILLIS = 120_000L
    private const val COOLDOWN_MILLIS = 10 * 60_000L
    private const val MAX_BANK_TRIPS = 8
    private const val MAX_SALE_ACTIONS = 128

    // Only native burnt-food waste may be dropped to stage an otherwise impossible withdrawal/proceeds slot.
    private val stagingJunk = setOf(2144, 2146, 323, 343, 357, 367, 369, 375, 381, 387, 393)

    /**
     * Runtime-only maintenance ownership and retry backoff, separate from persisted gameplay possessions.
     *
     * @author lare96
     */
    private class State(var active: Boolean = false, var nextAttempt: Long = 0L, var deferredUntil: Long = 0L) {
        val disposedUntil = LinkedHashMap<Int, Long>()
        val stagedIds = HashSet<Int>()
    }
    private val state = Attr.obj { State() }

    /**
     * Distinguishes an ordinary missing bank slot from an amount limit that disposing other goods cannot repair.
     *
     * @author lare96
     */
    internal data class DepositPlan(val newSlots: Int, val overflow: Boolean)

    /**
     * A full bank stack whose total guide value is the maximum authorized loss for this session.
     *
     * @author lare96
     */
    internal data class Candidate(val item: Item, val value: Long, val inventorySlots: Int)

    /**
     * Avoids repeatedly dispatching another capacity-dependent activity immediately after unsuccessful recovery.
     */
    @JvmStatic
    fun recoveryDeferred(bot: Bot): Boolean = bot.bank.computeRemainingSize() <= START_FREE_SLOTS &&
        System.currentTimeMillis() < bot.attributes()[state.attr].deferredUntil

    /**
     * Starts maintenance near capacity and retries only after the previous session's cooldown.
     */
    @JvmStatic
    fun needsMaintenance(bot: Bot): Boolean {
        val current = bot.attributes()[state.attr]
        return !bot.preferences.isMerchant && !current.active &&
            bot.bank.computeRemainingSize() <= START_FREE_SLOTS && System.currentTimeMillis() >= current.nextAttempt
    }

    /**
     * Suppresses opportunistic reacquisition of recently cleared waste while allowing new concrete supply requests.
     * Personal death recovery bypasses this filter at the pickup handler.
     */
    @JvmStatic
    fun recentlyDisposed(bot: Bot, id: Int): Boolean {
        val disposed = bot.attributes()[state.attr].disposedUntil
        disposed.entries.removeIf { it.value <= System.currentTimeMillis() }
        val normalized = baseId(id)
        return normalized in disposed && BotTradeIntentions.supplyTarget(bot, normalized) == 0 &&
            (retainedStock(bot)[normalized] ?: 0) == 0
    }

    private fun recordDisposal(bot: Bot, id: Int) {
        val disposed = bot.attributes()[state.attr].disposedUntil
        disposed.remove(baseId(id))
        disposed[baseId(id)] = System.currentTimeMillis() + 15 * 60_000L
        while (disposed.size > 64) disposed.remove(disposed.keys.first())
    }

    /**
     * Shares concrete and explicit reservations with planning, including an active or paused production session.
     */
    @JvmStatic
    fun retainedStock(bot: Bot): Map<Int, Int> {
        val retained = BotStoreSales.retainedStock(bot).toMutableMap()
        for (item in bot.equipment.filterNotNull()) retained[baseId(item.id)] = maxOf(1, retained[baseId(item.id)] ?: 0)
        for (script in bot.scriptStack.scripts.filter { !it.isFinished() && !it.isTerminated() }) {
            val ids = when (script) {
                is InventoryBotScript -> script.bankSpaceProtectedItems()
                is SkillingBotScript<*> -> script.tools().mapTo(HashSet()) { it.id }
                else -> emptySet()
            }
            for (id in ids) retained[baseId(id)] = maxOf(1, retained[baseId(id)] ?: 0)
        }
        return retained
    }

    /**
     * Deposits everything permitted by [except], merging existing stacks before considering a capacity recovery.
     * Interface, safety, amount-overflow and other interaction failures never authorize cleanup.
     */
    suspend fun depositInventory(bot: Bot, handler: BotActionHandler, except: Set<Int>): Boolean {
        if (!safe(bot) || !bot.bank.isOpen) return false
        val protectedIds = protectedCarried(bot, except)
        val merged = withTimeoutOrNull(30_000L) { mergeDeposits(bot, handler, except) } ?: return false
        if (!merged || !safe(bot) || !bot.bank.isOpen) return false
        val complete = deposited(bot, except)
        val current = bot.attributes()[state.attr]
        if (current.active) return complete
        if (complete) return true
        val plan = depositPlan(bot.bank.filterNotNull(), bot.inventory.filterNotNull().filter { it.id !in except })
        if (plan.overflow || plan.newSlots <= bot.bank.computeRemainingSize()) {
            bot.log("Bank deposit failed without a recoverable shortage of bank slots.")
            return false
        }
        if (current.nextAttempt > System.currentTimeMillis() || bot.preferences.isMerchant) {
            current.deferredUntil = System.currentTimeMillis() + COOLDOWN_MILLIS
            bot.log("Bank capacity recovery deferred: cooldown or protected merchant stock.")
            return false
        }
        clean(bot, handler, except, protectedIds, targetFreeSlots = 4)
        return finishDeposits(bot, handler, except)
    }

    /**
     * Performs proactive maintenance between activities; emergency deposits use the same implementation.
     */
    suspend fun maintain(bot: Bot, handler: BotActionHandler): Boolean {
        if (!needsMaintenance(bot) || !safe(bot)) return false
        val protectedIds = protectedCarried(bot, emptySet())
        val cleaned = clean(bot, handler, emptySet(), protectedIds)
        val restored = finishDeposits(bot, handler, emptySet())
        return cleaned && restored && bot.bank.computeRemainingSize() >= TARGET_FREE_SLOTS
    }

    private suspend fun clean(bot: Bot, handler: BotActionHandler, except: Set<Int>, protectedIds: Set<Int>,
                              targetFreeSlots: Int = TARGET_FREE_SLOTS): Boolean {
        val current = bot.attributes()[state.attr]
        if (current.active || bot.preferences.isMerchant || !safe(bot)) return false
        current.active = true
        current.nextAttempt = System.currentTimeMillis() + COOLDOWN_MILLIS
        val bankingMode = bot.varpManager.getValue(PersistentVarp.WITHDRAW_AS_NOTE)
        var success = false
        try {
            success = withTimeoutOrNull(SESSION_MILLIS) {
                cleanSession(bot, handler, except, protectedIds, targetFreeSlots)
            } == true
            return success
        } finally {
            current.active = false
            current.deferredUntil = if (success) 0L else System.currentTimeMillis() + COOLDOWN_MILLIS
            bot.varpManager.setValue(PersistentVarp.WITHDRAW_AS_NOTE, bankingMode)
            bot.overlays.closeWindows()
            bot.log("Bank maintenance ${if (success) "completed" else "deferred"}; " +
                "freeSlots=${bot.bank.computeRemainingSize()}, target=$targetFreeSlots.")
        }
    }

    private suspend fun cleanSession(bot: Bot, handler: BotActionHandler, except: Set<Int>, protectedIds: Set<Int>,
                                     targetFreeSlots: Int): Boolean {
        val sales = BotStoreSales(bot, handler)
        val failedIds = HashSet<Int>()
        val failedShops = HashMap<Int, MutableSet<Shop>>()
        var valueUsed = 0L
        var saleActions = 0
        var dropped = 0
        val authorizedRemaining = LinkedHashMap<Int, Int>()
        val authorizedPrice = HashMap<Int, Long>()
        repeat(MAX_BANK_TRIPS) { trip ->
            if (!safe(bot) || !handler.banking.travelToBankOpen() || !safe(bot)) return false
            handler.banking.clickBankingMode(false)
            if (!mergeDeposits(bot, handler, except)) return false
            if (deposited(bot, except) && bot.bank.computeRemainingSize() >= targetFreeSlots) return true

            // Native shops require proceeds room before they remove sold goods. Never drop an arbitrary cheap item.
            val proceedsSlot = if (bot.inventory.contains(995) && bot.inventory.hasSpaceFor(Item(995))) 0 else 1
            val stagingSpace = 1 + proceedsSlot
            while (bot.inventory.computeRemainingSize() < stagingSpace && dropped < 2) {
                val reserved = retainedStock(bot)
                val junk = bot.inventory.filterNotNull().firstOrNull {
                    it.id in stagingJunk && it.id !in except && !it.isDynamic && it.itemDef.isTradeable &&
                        (reserved[baseId(it.id)] ?: 0) == 0
                } ?: break
                if (!safe(bot)) return false
                bot.overlays.closeWindows()
                val before = bot.inventory.computeAmountForId(junk.id)
                recordDisposal(bot, junk.id)
                if (!handler.inventory.dropItem(junk.id) || bot.inventory.computeAmountForId(junk.id) >= before) return false
                dropped++
            }
            if (bot.inventory.computeRemainingSize() < stagingSpace) {
                bot.log("Bank maintenance deferred: no safe inventory staging space or proceeds slot.")
                return false
            }
            if (!bot.bank.isOpen && (!handler.banking.travelToBankOpen() || !safe(bot))) return false
            sales.refreshRetention()
            val reserve = retainedStock(bot)
            val candidates = candidates(bot.bank.filterNotNull(), reserve, protectedIds + failedIds,
                { world.economy.getPrice(it) }, { sales.shopsFor(sales.saleId(it)).isNotEmpty() })
                .filter { it.item.amount <= (authorizedRemaining[it.item.id] ?: Int.MAX_VALUE) }
                .sortedBy { if (it.item.id in authorizedRemaining) 0 else 1 }
            var space = bot.inventory.computeRemainingSize() - proceedsSlot
            var plannedValue = 0L
            var plannedUnits = 0L
            val batch = ArrayList<Candidate>()
            val pendingSlots = depositPlan(bot.bank.filterNotNull(), bot.inventory.filterNotNull()
                .filter { it.id !in except }).newSlots
            var projectedFreed = 0
            for (candidate in candidates) {
                if (space < 1) break
                val id = candidate.item.id
                val quote = maxOf(authorizedPrice[id] ?: 0L, candidate.value / candidate.item.amount)
                val saleId = sales.saleId(id)
                val stackable = ItemDefinition.ALL.retrieve(saleId).isStackable
                val batchCapacity = if (stackable) candidate.item.amount else space
                val remainingTrips = MAX_BANK_TRIPS - trip
                if (candidate.item.amount > batchCapacity && batch.isNotEmpty()) continue
                if (!canCompleteStack(candidate.item.amount, batchCapacity, remainingTrips,
                        MAX_SALE_ACTIONS - saleActions) ||
                    valueUsed + plannedValue + quote * candidate.item.amount > MAX_SESSION_VALUE) continue
                val amount = if (stackable) candidate.item.amount else minOf(candidate.item.amount, space)
                val batchValue = quote * amount
                if (valueUsed + plannedValue + batchValue > MAX_SESSION_VALUE ||
                    plannedUnits + amount > (MAX_SALE_ACTIONS - saleActions) * 100L) continue
                authorizedRemaining.putIfAbsent(id, candidate.item.amount)
                authorizedPrice[id] = quote
                val slots = if (stackable) 1 else amount
                batch += Candidate(candidate.item.withAmount(amount), batchValue, slots)
                plannedValue += batchValue
                plannedUnits += amount
                space -= slots
                if (amount == candidate.item.amount) projectedFreed++
                if (bot.bank.computeRemainingSize() + projectedFreed >= targetFreeSlots + pendingSlots) break
            }
            if (batch.isEmpty()) {
                bot.log("Bank maintenance deferred: no completely removable low-value stack within retention and sale limits.")
                return false
            }

            val withdrawn = ArrayList<Pair<Candidate, Int>>()
            for (candidate in batch) {
                if (!safe(bot) || !bot.bank.isOpen) return false
                val id = candidate.item.id
                if ((retainedStock(bot)[id] ?: 0) > 0 || id in protectedIds ||
                    bot.bank.computeNonDynamicAmountForId(id) != authorizedRemaining[id]) continue
                val saleId = sales.saleId(id)
                handler.banking.clickBankingMode(saleId != id)
                val bankBefore = bot.bank.computeNonDynamicAmountForId(id)
                val before = bot.inventory.computeAmountForId(saleId)
                bot.attributes()[state.attr].stagedIds += saleId
                if (!handler.banking.withdraw(candidate.item)) { failedIds += id; continue }
                withdrawn += candidate to saleId
                if (bot.bank.computeNonDynamicAmountForId(id) != bankBefore - candidate.item.amount ||
                    bot.inventory.computeAmountForId(saleId).toLong() - before != candidate.item.amount.toLong()) {
                    failedIds += id
                    break
                }
            }
            if (withdrawn.isEmpty() || !sales.travelHome()) return false
            for ((candidate, saleId) in withdrawn) {
                val id = candidate.item.id
                val rejected = failedShops.getOrPut(id) { HashSet() }
                val ownedBefore = bot.inventory.computeAmountForId(saleId)
                for (shop in sales.shopsFor(saleId).filterNot { it in rejected }) {
                    if (!safe(bot) || saleActions >= MAX_SALE_ACTIONS) break
                    if (!sales.openShop(shop)) { rejected += shop; continue }
                    val quote = authorizedPrice.getValue(id)
                    if (valueUsed + quote * bot.inventory.computeAmountForId(saleId) > MAX_SESSION_VALUE) break
                    val sold = sales.sellItem(shop, saleId, candidate.item.amount, MAX_SALE_ACTIONS - saleActions,
                        canContinue = {
                            safe(bot) && saleActions < MAX_SALE_ACTIONS && id !in protectedIds &&
                                (retainedStock(bot)[id] ?: 0) == 0
                        }, beforeAction = { saleActions++ })
                    if (sold > 0) {
                        valueUsed += quote * sold
                        authorizedRemaining[id] = (authorizedRemaining.getValue(id) - sold).coerceAtLeast(0)
                        if (!bot.inventory.contains(saleId)) {
                            bot.attributes()[state.attr].stagedIds -= saleId
                            if (!bot.bank.contains(id)) recordDisposal(bot, id)
                        }
                        if (!bot.inventory.contains(saleId)) break
                    }
                    rejected += shop
                }
                if (bot.inventory.computeAmountForId(saleId) >= ownedBefore || bot.inventory.contains(saleId)) failedIds += id
            }

            // Restore unsold selected goods first, while the slots their withdrawal released are still reserved.
            if (!safe(bot) || !handler.banking.travelToBankOpen() || !safe(bot)) return false
            for ((_, saleId) in withdrawn) {
                if (!bot.inventory.contains(saleId)) continue
                if (!handler.banking.depositAll(saleId) || bot.inventory.contains(saleId)) return false
            }
            if (!mergeDeposits(bot, handler, except)) return false
            if (deposited(bot, except) && bot.bank.computeRemainingSize() >= targetFreeSlots) return true
            if (saleActions >= MAX_SALE_ACTIONS || valueUsed >= MAX_SESSION_VALUE) return false
        }
        return false
    }

    /**
     * Computes native normalized slot demand without mistaking a full existing stack's amount overflow for capacity.
     */
    internal fun depositPlan(bank: List<Item>, inventory: List<Item>): DepositPlan {
        var newSlots = inventory.count { it.isDynamic }
        var overflow = false
        for ((id, items) in inventory.filterNot { it.isDynamic }.groupBy { baseId(it.id) }) {
            val amount = items.sumOf { it.amount.toLong() }
            val existing = bank.filter { !it.isDynamic && it.id == id }.sumOf { it.amount.toLong() }
            if (existing == 0L) newSlots++
            if (amount + existing > Int.MAX_VALUE) overflow = true
        }
        return DepositPlan(newSlots, overflow)
    }

    /**
     * Orders affordable whole stacks by total guide loss per released bank slot. Partial surplus is not slot relief.
     */
    internal fun candidates(bank: List<Item>, retained: Map<Int, Int>, protectedIds: Set<Int>,
                            guide: (Int) -> Long, accepted: (Int) -> Boolean): List<Candidate> =
        bank.groupBy { it.id }.mapNotNull { (id, items) ->
            if (id == 995 || id in protectedIds || (retained[id] ?: 0) > 0 || items.size != 1) return@mapNotNull null
            val item = items.single()
            if (item.isDynamic || item.amount < 1 || !item.itemDef.isTradeable || item.itemDef.isNoted) return@mapNotNull null
            val price = guide(id)
            if (price <= 0 || price > MAX_STACK_VALUE / item.amount) return@mapNotNull null
            val value = price * item.amount
            if (value > MAX_STACK_VALUE || !accepted(id)) return@mapNotNull null
            Candidate(item, value, if (item.itemDef.isStackable) 1 else item.amount)
        }.sortedWith(compareBy<Candidate> { it.value }.thenBy { it.inventorySlots }.thenBy { it.item.id })

    /**
     * Rejects a complete-stack commitment before selling anything when its required trips or widget clicks exceed
     * this session's remaining budget. Normal Sell-10, Sell-5 and Sell-1 choices determine the click count.
     */
    internal fun canCompleteStack(amount: Int, batchCapacity: Int, trips: Int, actions: Int): Boolean {
        if (amount < 1 || batchCapacity < 1 || trips < 1 || actions < 1) return false
        if (amount.toLong() > batchCapacity.toLong() * trips) return false
        fun clicks(units: Int): Long = (units + 99L) / 100L
        val fullBatches = amount / batchCapacity
        val remainder = amount % batchCapacity
        return fullBatches * clicks(batchCapacity) + clicks(remainder) <= actions
    }

    private suspend fun mergeDeposits(bot: Bot, handler: BotActionHandler, except: Set<Int>): Boolean {
        if (!safe(bot) || !bot.bank.isOpen) return false
        val staged = bot.attributes()[state.attr].stagedIds
        staged.removeIf { !bot.inventory.contains(it) }
        val ids = bot.inventory.filterNotNull().map { it.id }.distinct().filterNot { it in except }
            .sortedBy { if (it in staged) 0 else 1 }
        for (id in ids) {
            var attempts = 0
            while (attempts++ < bot.inventory.capacity()) {
                val item = bot.inventory.firstOrNull { it != null && it.id == id } ?: break
                if (!safe(bot) || !bot.bank.isOpen) return false
                val amount = if (item.isDynamic) 1 else bot.inventory.computeNonDynamicAmountForId(id)
                val incoming = item.withAmount(amount).withId(baseId(id))
                if (amount > 0 && !bot.bank.hasSpaceFor(incoming)) break
                val before = bot.inventory.computeAmountForId(id)
                val slotsBefore = bot.inventory.count { it != null && it.id == id }
                if (!handler.banking.depositAll(id) || !safe(bot) || !bot.bank.isOpen ||
                    bot.inventory.computeAmountForId(id) >= before &&
                    bot.inventory.count { it != null && it.id == id } >= slotsBefore) return false
            }
            if (!bot.inventory.contains(id)) staged -= id
        }
        return true
    }

    private suspend fun finishDeposits(bot: Bot, handler: BotActionHandler, except: Set<Int>): Boolean {
        if (!safe(bot)) return false
        if (deposited(bot, except) && bot.bank.isOpen) return true
        return withTimeoutOrNull(20_000L) {
            safe(bot) && handler.banking.travelToBankOpen() && safe(bot) &&
                mergeDeposits(bot, handler, except) && deposited(bot, except)
        } == true
    }

    private fun protectedCarried(bot: Bot, except: Set<Int>): Set<Int> =
        (bot.inventory.filterNotNull().filterNot { it.id in stagingJunk }.map { baseId(it.id) } +
            except.map { baseId(it) }).toSet()

    private fun deposited(bot: Bot, except: Set<Int>): Boolean = bot.inventory.none { it != null && it.id !in except }

    private fun baseId(id: Int): Int = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)

    private fun safe(bot: Bot): Boolean = bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot)
}
