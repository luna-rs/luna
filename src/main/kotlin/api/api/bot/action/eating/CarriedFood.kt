package api.bot.action.eating

import game.player.item.consume.food.Food
import io.luna.game.model.mob.bot.Bot

/**
 * A piece of food in a bot's inventory.
 *
 * @property index The inventory slot it's in.
 * @property id The item id, which tells a started pizza or cake apart from a whole one.
 * @property food The kind of food.
 * @author lare96
 */
data class CarriedFood(val index: Int, val id: Int, val food: Food) {

    companion object {

        /**
         * Lists the food in [bot]'s inventory, in slot order.
         */
        fun of(bot: Bot): List<CarriedFood> =
            bot.inventory.withIndex().mapNotNull { (index, item) ->
                item?.let { Food.ID_TO_FOOD[it.id]?.let { food -> CarriedFood(index, it.id, food) } }
            }

        /**
         * Picks the food to eat.
         *
         * - When the bot's life is in danger, it eats its biggest food.
         * - A careful bot eats the biggest food that heals no more than it's missing, finishing a started pizza, pie
         * or cake before starting another. When every food would heal more than it's missing, it eats the smallest.
         * - Otherwise the bot eats the first food in its inventory, as most players do.
         *
         * @param foods The food the bot is carrying, in slot order.
         * @param missingHp How many hitpoints the bot is missing, as far as it can tell.
         * @param inDanger Whether the bot's life is in danger.
         * @param careful Whether the bot picks its food carefully.
         * @return The food to eat, or `null` if there is none.
         */
        fun choose(foods: List<CarriedFood>, missingHp: Int, inDanger: Boolean, careful: Boolean): CarriedFood? {
            if (foods.isEmpty()) {
                return null
            }
            if (inDanger) {
                return foods.maxBy { it.food.heal }
            }
            if (!careful) {
                return foods.first()
            }
            val fitting = foods.filter { it.food.heal <= missingHp }
            if (fitting.isEmpty()) {
                return foods.minBy { it.food.heal }
            }
            val heal = fitting.maxOf { it.food.heal }
            return fitting.filter { it.food.heal == heal }.let { best -> best.firstOrNull { it.isStarted } ?: best.first() }
        }
    }

    /**
     * Whether some of this food has already been eaten, like half a pizza or two thirds of a cake.
     */
    val isStarted: Boolean
        get() = id != food.id
}
