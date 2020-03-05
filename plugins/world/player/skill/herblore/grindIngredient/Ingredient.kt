package world.player.skill.herblore.grindIngredient

import io.luna.game.model.item.Item

/**
 * An enum representing an item used with a pestle and mortar.
 */
enum class Ingredient(val id: Int, val newId: Int) {
    CHOCOLATE_DUST(id = 1973,
                   newId = 1975),
    DRAGON_SCALE_DUST(id = 243,
                      newId = 241),
    CRUSHED_NEST(id = 5075,
                 newId = 6693),
    UNICORN_HORN_DUST(id = 237,
                      newId = 235);

    companion object {

        /**
         * The pestle and mortar identifier.
         */
        const val PESTLE_AND_MORTAR = 233

        /**
         * Mappings of [Ingredient.id] to [Ingredient] instances.
         */
        val OLD_TO_INGREDIENT = values().associateBy { it.id }
    }

    /**
     * The original item.
     */
    val oldItem = Item(id)

    /**
     * The grinded item.
     */
    val newItem = Item(newId)
}