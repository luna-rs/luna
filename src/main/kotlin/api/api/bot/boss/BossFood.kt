package api.bot.boss

import game.player.Sound
import game.player.item.consume.food.Food
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.bot.Bot

/**
 * Chooses real provisions by their remaining healing and individual bites for each kind of boss trip.
 * Half-eaten foods retain only their remaining bites; drinks and non-food leftovers never supply the budget.
 *
 * @author lare96
 */
internal object BossFood {
    /**
     * One trip's healing budget and minimum useful bite, independent of item names or ownership location.
     *
     * @property minimumBiteHealing Smallest individual heal accepted for this trip.
     * @property requiredHealing Total remaining healing needed before departure.
     * @author lare96
     */
    data class Policy(val minimumBiteHealing: Int, val requiredHealing: Int)

    /**
     * The actual edible remainder of a single physical item.
     *
     * @property food Native food entry owning this portion.
     * @property biteHealing Healing from one native click.
     * @property remainingBites Number of edible portions still held by this item.
     * @property healing Total remaining healing, including subsequent portions exactly once.
     * @property delay Native delay between bites.
     * @author lare96
     */
    data class Portion(val food: Food, val biteHealing: Int, val remainingBites: Int,
                       val healing: Int, val delay: Int)

    /**
     * Gives grouped outings smaller budgets while retaining stronger recovery for dangerous bosses.
     */
    fun policy(target: BossTarget, group: Boolean): Policy = when (target) {
        BossTarget.MOLE -> if (group) Policy(7, 140) else Policy(10, 224)
        BossTarget.KBD -> if (group) Policy(8, 180) else Policy(12, 252)
        BossTarget.CHAOS_ELEMENTAL -> if (group) Policy(10, 220) else Policy(14, 280)
        BossTarget.KALPHITE_QUEEN -> if (group) Policy(11, 240) else Policy(14, 280)
    }

    /**
     * Follows the native portion chain rather than the unordered family ID set.
     * IDs outside that chain, including notes and empty bowls, supply no edible healing.
     */
    fun portion(id: Int): Portion? {
        val food = Food.ID_TO_FOOD[id] ?: return null
        if (food.heal <= 0 || food.sound == Sound.LIQUID) return null
        var current: Int? = id
        var bites = 0
        while (current != null && bites < 4) {
            if (Food.ID_TO_FOOD[current] !== food) return null
            bites++
            current = food.getNextId(current)
        }
        if (current != null) return null
        return Portion(food, food.heal, bites, food.heal * bites, food.delay)
    }

    /**
     * Packs the densest actual stock first, resolving ties by bite strength and then item ID.
     * The returned quantities describe at most twenty physical food slots and never alter the supplied stock.
     */
    fun plan(stock: Map<Int, Int>, policy: Policy, slots: Int = 20): List<Item>? {
        if (policy.requiredHealing <= 0) return emptyList()
        var remainingSlots = slots.coerceIn(0, 20)
        val candidates = stock.entries.mapNotNull { (id, amount) ->
            if (amount <= 0) null else portion(id)?.takeIf { it.biteHealing >= policy.minimumBiteHealing }
                ?.let { Triple(id, amount, it) }
        }.sortedWith(compareByDescending<Triple<Int, Int, Portion>> { it.third.healing }
            .thenByDescending { it.third.biteHealing }.thenBy { it.first })
        val result = ArrayList<Item>()
        var healing = 0L
        for ((id, available, portion) in candidates) {
            val needed = ((policy.requiredHealing.toLong() - healing + portion.healing - 1) / portion.healing)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val count = minOf(available, remainingSlots, needed)
            if (count <= 0) continue
            result += Item(id, count)
            healing += portion.healing.toLong() * count
            remainingSlots -= count
            if (healing >= policy.requiredHealing) return result
        }
        return null
    }

    /**
     * Reads physical container quantities without consulting trade wishes, pending orders or hypothetical supplies.
     */
    fun stock(vararg containers: ItemContainer): Map<Int, Int> {
        val result = HashMap<Int, Int>()
        for (container in containers) for (item in container) {
            if (item == null || item.amount <= 0 || portion(item.id) == null) continue
            result[item.id] = ((result[item.id] ?: 0).toLong() + item.amount)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        }
        return result
    }

    /**
     * Verifies that the owned inventory and bank can supply a complete trip within twenty food slots.
     */
    fun ownedReady(bot: Bot, target: BossTarget, group: Boolean): Boolean =
        plan(stock(bot.inventory, bot.bank), policy(target, group)) != null

    /**
     * Sums only usable remaining healing physically carried by this participant.
     */
    fun carriedHealing(bot: Bot, target: BossTarget, group: Boolean): Int = carriedHealing(bot, policy(target, group))

    fun carriedHealing(bot: Bot, policy: Policy): Int {
        return stock(bot.inventory).entries.sumOf { (id, amount) ->
            val portion = portion(id)!!
            if (portion.biteHealing >= policy.minimumBiteHealing) portion.healing.toLong() * amount else 0L
        }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Keeps at least two qualifying bites for leaving the encounter.
     */
    fun reserveHealing(target: BossTarget, group: Boolean): Int = policy(target, group).minimumBiteHealing * 2

    /**
     * Withdraws the missing real healing through the native bank handler after tools have occupied their slots.
     * Partial or falsely acknowledged withdrawals fail without discarding or replacing any received items.
     */
    suspend fun withdraw(bot: Bot, target: BossTarget, group: Boolean): Boolean = withdraw(bot, policy(target, group))

    suspend fun withdraw(bot: Bot, policy: Policy): Boolean {
        val carried = carriedHealing(bot, policy)
        if (carried >= policy.requiredHealing) return true
        if (!bot.bank.isOpen) return false
        val carriedSlots = stock(bot.inventory).entries.sumOf { (id, amount) ->
            if (portion(id)!!.biteHealing >= policy.minimumBiteHealing) amount.coerceAtMost(28) else 0
        }
        val freeSlots = minOf(20 - carriedSlots, bot.inventory.computeRemainingSize()).coerceAtLeast(0)
        val plan = plan(stock(bot.bank), policy.copy(requiredHealing = policy.requiredHealing - carried), freeSlots)
            ?: return false
        for (item in plan) {
            val before = bot.inventory.computeAmountForId(item.id)
            if (!bot.actionHandler.banking.withdraw(item) ||
                bot.inventory.computeAmountForId(item.id) - before < item.amount) return false
        }
        return carriedHealing(bot, policy) >= policy.requiredHealing
    }

    /**
     * Takes one native bite from a qualifying carried portion, preferring stronger and already-started food.
     */
    suspend fun eat(bot: Bot, target: BossTarget, group: Boolean): Boolean = eat(bot, policy(target, group))

    suspend fun eat(bot: Bot, policy: Policy): Boolean {
        val selected = bot.inventory.withIndex().mapNotNull { (slot, item) ->
            if (item == null) null else portion(item.id)?.takeIf { it.biteHealing >= policy.minimumBiteHealing }
                ?.let { Triple(slot, item.id, it) }
        }.maxWithOrNull(compareBy<Triple<Int, Int, Portion>> { it.third.biteHealing }
            .thenBy { -it.third.remainingBites }) ?: return false
        return bot.actionHandler.inventory.eat(selected.first, selected.second)
    }
}
