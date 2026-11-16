package api.bot.action

import api.bot.script.BotScript.ActionSafety
import game.player.item.consume.potion.PotionDecanting
import game.player.item.consume.potion.PotionFamily
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the same inventory potion-pair action as a player and verifies its real slot outputs.
 * This helper neither withdraws nor deposits items, creates supplies, or mutates bottles directly.
 * Callers own bank capacity, purchase limits and session permission; interrupted work retains actual partial results.
 *
 * @author lare96
 */
object BotPotionDecanting {

    /**
     * Sends one normal distinct-slot pair action and waits at most three seconds for its exact result.
     */
    suspend fun combine(bot: Bot, sourceSlot: Int, targetSlot: Int, allowed: () -> Boolean = { true }): Boolean {
        fun permitted() = allowed() && bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot)
        if (!permitted()) return false
        val inventory = bot.inventory
        if (sourceSlot !in 0 until inventory.capacity() || targetSlot !in 0 until inventory.capacity() ||
            sourceSlot == targetSlot) return false
        val source = inventory[sourceSlot] ?: return false
        val target = inventory[targetSlot] ?: return false
        if (!isBottle(source) || !isBottle(target)) return false
        val outputs = PotionDecanting.outputs(source.id, target.id) ?: return false
        if (!bot.actionHandler.inventory.useItem(source.id, sourceSlot).onItem(target.id, targetSlot)) return false
        return withTimeoutOrNull(3.seconds) {
            while (true) {
                val currentSource = inventory[sourceSlot]
                val currentTarget = inventory[targetSlot]
                if (currentSource?.id == outputs.first && currentTarget?.id == outputs.second &&
                    isBottle(currentSource) && isBottle(currentTarget)) return@withTimeoutOrNull true
                if (!permitted() || currentSource != source || currentTarget != target) return@withTimeoutOrNull false
                delay(100)
            }
        } == true
    }

    /**
     * Consolidates only carried partial bottles, performing at most one inventory-capacity worth of pair actions.
     * Each successful pair removes a partial bottle from further work, so the scan and operation count stay bounded.
     * The returned count includes verified operations before permission loss, timeout or a failed pair.
     */
    suspend fun consolidateInventory(bot: Bot, family: PotionFamily, allowed: () -> Boolean = { true }): Int {
        var progress = 0
        withTimeoutOrNull(30.seconds) {
            repeat(bot.inventory.capacity()) {
                if (!allowed()) return@withTimeoutOrNull
                val partials = bot.inventory.withIndex().filter { (_, item) ->
                    item != null && isBottle(item) && item.id in family.ids && family.doses(item.id) in 1..3
                }
                if (partials.size < 2) return@withTimeoutOrNull
                if (!combine(bot, partials[0].index, partials[1].index, allowed)) return@withTimeoutOrNull
                progress++
            }
        }
        return progress
    }

    private fun isBottle(item: Item): Boolean = item.amount == 1 && !item.isDynamic && !item.itemDef.isNoted
}
