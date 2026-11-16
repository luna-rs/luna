package api.bot.action

import api.bot.script.BotScript.ActionSafety
import api.attr.Attr
import game.player.item.consume.potion.PotionFamily
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.ceil
import kotlin.time.Duration.Companion.seconds

/**
 * Prepares personally needed potions at an already open bank using owned bottles and normal inventory actions.
 * A small partial-bottle batch is consolidated before useful carried doses are selected. Full banks skip optional
 * consolidation; interruption and failed deposits leave actual bottles and empty vials owned by the bot.
 *
 * @author lare96
 */
object BotPotionMaintenance {
    private const val EMPTY_VIAL = 229
    private const val RESERVE_DOSES = 12
    private const val CARRY_DOSES = 4
    private val nextFamily = Attr.int()

    /**
     * Consolidates relevant owned remnants and packs up to one former four-dose bottle's worth for each family.
     * Purchases and speculative conversion are separate; this operation never creates a shopping request.
     */
    suspend fun prepareOwned(bot: Bot, families: Collection<PotionFamily>, allowed: () -> Boolean = { true }): Boolean {
        val distinct = families.distinctBy { it.fourDose }.take(PotionFamily.ALL.size)
        if (distinct.isEmpty() || !bot.bank.isOpen) return true
        val cursor = bot.attributes()[nextFamily.attr].coerceAtLeast(0) % distinct.size
        val selected = List(distinct.size) { distinct[(cursor + it) % distinct.size] }
        fun permitted() = allowed() && bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot)
        if (!permitted()) return false
        val banking = bot.actionHandler.banking
        withTimeoutOrNull(30.seconds) {
            for ((index, family) in selected.withIndex()) {
                if (!permitted() || !bot.bank.isOpen) return@withTimeoutOrNull
                bot.attributes()[nextFamily.attr] = (cursor + index + 1) % distinct.size
                banking.clickBankingMode(false)
                val partialIds = family.ids.take(3)
                val partials = partialIds.sumOf { id ->
                    bot.inventory.computeAmountForId(id).toLong() + bot.bank.computeAmountForId(id)
                }
                // Reserving every possible resulting type avoids optional rearrangement when storage is full.
                val storageFits = bot.bank.hasSpaceForAll((family.ids + EMPTY_VIAL).map { Item(it) })
                if (partials >= 2 && storageFits) {
                    var carried = partialIds.sumOf { id -> bot.inventory.computeAmountForId(id).toLong() * family.doses(id) }
                    for (id in partialIds.reversed()) {
                        if (!permitted() || !bot.bank.isOpen) return@withTimeoutOrNull
                        val needed = ceil((RESERVE_DOSES - carried).coerceAtLeast(0L).toDouble() / family.doses(id)).toInt()
                        val quantity = minOf(needed, bot.bank.computeAmountForId(id), bot.inventory.computeRemainingSize())
                        if (quantity > 0) banking.withdraw(Item(id, quantity))
                        carried = partialIds.sumOf { bot.inventory.computeAmountForId(it).toLong() * family.doses(it) }
                    }
                    val carriedPartials = bot.inventory.count { item -> item != null && item.id in partialIds &&
                        item.amount == 1 && !item.isDynamic && !item.itemDef.isNoted }
                    if (carriedPartials >= 2 && permitted()) {
                        bot.overlays.closeWindows()
                        BotPotionDecanting.consolidateInventory(bot, family, ::permitted)
                        if (!permitted() || !banking.travelToBankOpen()) return@withTimeoutOrNull
                        for (id in family.ids + EMPTY_VIAL) {
                            if (!permitted()) return@withTimeoutOrNull
                            if (bot.inventory.contains(id) && !banking.depositAll(id)) break
                        }
                    }
                }
                if (!permitted() || !bot.bank.isOpen) return@withTimeoutOrNull
                var carried = family.ids.sumOf { bot.inventory.computeAmountForId(it).toLong() * family.doses(it) }
                for (id in family.ids.reversed()) {
                    if (carried >= CARRY_DOSES || !permitted()) break
                    val needed = ceil((CARRY_DOSES - carried).toDouble() / family.doses(id)).toInt()
                    val quantity = minOf(needed, bot.bank.computeAmountForId(id), bot.inventory.computeRemainingSize())
                    if (quantity > 0) banking.withdraw(Item(id, quantity))
                    carried = family.ids.sumOf { bot.inventory.computeAmountForId(it).toLong() * family.doses(it) }
                }
            }
        }
        if (!permitted()) return false
        return bot.bank.isOpen || withTimeoutOrNull(5.seconds) { banking.travelToBankOpen() } == true
    }
}
