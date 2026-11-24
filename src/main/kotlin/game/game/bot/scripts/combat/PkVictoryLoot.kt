package game.bot.scripts.combat

import api.bot.Suspendable.waitFor
import api.bot.action.BotActionHandler
import api.bot.action.BotLootActionHandler
import api.bot.action.PkLoadoutPreparation
import api.bot.action.eating.CarriedFood
import api.combat.death.DeathHookHandler.deathItems
import game.player.item.consume.food.Food
import game.player.item.consume.food.Food.Companion.lastEat
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Heals a PK bot, collects valuable loot, and replenishes food after a win.
 *
 * @author lare96
 */
internal class PkVictoryLoot(private val bot: Bot, private val handler: BotActionHandler) {

    /**
     * Eats carried food and collects food for immediate healing, then gives valuables first use of inventory space.
     * Remaining food can refill the bot's supplies toward its normal PK food target.
     */
    suspend fun collect(items: Iterable<GroundItem>, canContinue: () -> Boolean,
                        allowsDrop: (GroundItem) -> Boolean) {
        suspend fun pickUp(item: GroundItem) = handler.loot.pickup(item) { canContinue() && allowsDrop(item) }

        eatCarriedFood(canContinue)
        if (!canContinue()) return
        val ordered = handler.loot.ordered(items, BotLootActionHandler.Purpose.PLAYER_COMBAT)
        val (recovery, remaining) = ordered.partition { it in bot.deathItems }
        for (item in recovery) {
            if (!canContinue()) return
            if (pickUp(item) && isFood(item.id)) eatCarriedFood(canContinue)
        }

        val (food, other) = remaining.partition { isFood(it.id) }
        val (valuables, supplies) = other.partition {
            it.id == Currency.COINS.id || handler.loot.value(it) > BotLootActionHandler.VALUABLE_LOOT_VALUE
        }
        val remainingFood = food.toMutableList()
        val healingFood = remainingFood.iterator()
        while (healingFood.hasNext() && canContinue() && bot.health in 1 until bot.totalHealth &&
            carriedFoodCount() == 0L) {
            if (pickUp(healingFood.next())) {
                healingFood.remove()
                eatCarriedFood(canContinue)
            }
        }

        for (item in valuables) {
            if (!canContinue()) return
            pickUp(item)
        }
        for (item in remainingFood) {
            if (!canContinue()) return
            if (carriedFoodCount() >= PkLoadoutPreparation.FOOD_TARGET) break
            if (pickUp(item)) eatCarriedFood(canContinue)
        }
        for (item in supplies) {
            if (!canContinue()) return
            pickUp(item)
        }
    }

    private fun isFood(id: Int): Boolean = (Food.ID_TO_FOOD[id]?.heal ?: 0) > 0

    private fun carriedFoodCount(): Long =
        bot.inventory.sumOf { if (it != null && isFood(it.id)) it.amount.toLong() else 0L }

    private suspend fun eatCarriedFood(canContinue: () -> Boolean) {
        val lastEat = bot.lastEat
        while (canContinue() && bot.health in 1 until bot.totalHealth) {
            val missing = bot.totalHealth - bot.health
            val food = CarriedFood.choose(CarriedFood.of(bot).filter { it.food.heal > 0 },
                missing, bot.healthPercent < PkCombatRecovery.SURVIVAL_HEALTH_PERCENT, careful = true) ?: return
            if (lastEat.isRunning && lastEat.durationTicks < food.food.delay &&
                !waitFor(4.seconds) { !canContinue() || !lastEat.isRunning || lastEat.durationTicks >= food.food.delay }) {
                return
            }
            val before = bot.health
            if (!canContinue() || before >= bot.totalHealth ||
                !handler.inventory.eat(food.index, food.id) || bot.health <= before) return
        }
    }
}
