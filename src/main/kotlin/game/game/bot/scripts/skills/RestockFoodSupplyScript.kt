package game.bot.scripts.skills

import api.bot.action.FoodSupplyAcquisition
import api.bot.script.DynamicBotScript
import game.skill.cooking.cookFood.Food
import io.luna.game.model.mob.bot.Bot

/**
 * Rebuilds meals for an existing personal need and resumes after the native fishing and cooking legs.
 *
 * @author lare96
 */
class RestockFoodSupplyScript(bot: Bot, private val cookedChecked: Boolean = false) : DynamicBotScript(bot) {
    private var fishingStarted = false
    private var cookingStarted = false
    private val attemptedRecipes = mutableSetOf<Food>()
    private var openingStock = -1

    override suspend fun run(): Boolean {
        val supplies = FoodSupplyAcquisition(bot, handler)
        if (cookingStarted && !fishingStarted) {
            supplies.observeCookedStock()
            return true
        }
        fun allowed() = !isPaused() && !isTerminated() && bot.scriptStack.current() === this
        if (!fishingStarted && !cookingStarted) {
            val stock = FoodSupplyAcquisition.stock(bot)
            if (openingStock >= 0 && stock > openingStock) {
                supplies.observeCookedStock()
                if (allowed() && isActionSafe()) handler.banking.travelToBankDepositAll()
                return true
            }
            openingStock = stock
        }
        val next = if (fishingStarted) supplies.cookOwned(::allowed, attemptedRecipes)
            else supplies.restock(::allowed, cookedChecked)
        if (next == null || !allowed()) {
            if (cookingStarted) supplies.observeCookedStock()
            return true
        }
        if (next is FishBotScript) fishingStarted = true
        if (next is CookFoodBotScript) {
            cookingStarted = true
            next.selectedFood?.let(attemptedRecipes::add)
        }
        bot.scriptStack.pushHead(next)
        return false
    }
}
