package engine.bot.coordinator.skill

import api.bot.script.ProductionRecipe
import api.predef.SKILL_COOKING
import game.player.item.consume.food.Food

/**
 * Applies food-specific eligibility to the shared bounded native preparation traversal.
 * Cooking retains its deterministic zero-experience prefixes and exact edible output identities.
 *
 * @author lare96
 */
internal object CookingProductionPaths {
    /**
     * Whether the native consumable registry recognizes this exact finished or partly eaten food id.
     */
    fun isFood(id: Int): Boolean = id in Food.ID_TO_FOOD

    /**
     * Keeps prepared ingredients within their captured complete basket's conservative value.
     */
    fun pricePreparations(path: List<ProductionRecipe>, prices: MutableMap<Int, Long>) =
        ProductionRecipePaths.pricePreparations(path, prices)

    /**
     * Returns native Cooking suffixes and zero-experience preparation for one actual edible output.
     */
    fun forOutput(output: Int,
                  describe: (ProductionCatalogue.Entry) -> ProductionRecipe?): List<List<ProductionRecipe>> =
        if (!isFood(output)) emptyList() else
            ProductionRecipePaths.forOutput(output, SKILL_COOKING, describe) { it.experience == 0.0 }
}
