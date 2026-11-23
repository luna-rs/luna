package game.skill.herblore.coconutMilk

import api.predef.*
import io.luna.game.model.item.Item

/**
 * Unfinished potions mixed in a vial of coconut milk instead of a vial of water. They are finished in
 * [game.skill.herblore.makePotion.FinishedPotion].
 *
 * @property id The unfinished potion.
 * @property ingredient The ingredient put into the coconut milk.
 * @property level The Herblore level of the finished potion.
 * @author TheLining
 */
enum class CoconutPotion(val id: Int, val ingredient: Int, val level: Int) {
    EXTRA_STRONG_WEAPON_POISON(id = 5936, ingredient = 6016, level = 73),
    SUPER_STRONG_WEAPON_POISON(id = 5939, ingredient = 2398, level = 82);

    companion object {

        /**
         * A vial of coconut milk.
         */
        const val COCONUT_MILK = 5935

        /**
         * A whole coconut.
         */
        const val COCONUT = 5974

        /**
         * A coconut broken open with a hammer.
         */
        const val COCONUT_HALF = 5976

        /**
         * The shell left once the milk is poured out.
         */
        const val COCONUT_SHELL = 5978

        /**
         * An empty vial.
         */
        const val VIAL = 229

        /**
         * A hammer.
         */
        const val HAMMER = 2347
    }

    /**
     * The unfinished potion item.
     */
    val idItem = Item(id)

    /**
     * The ingredient item.
     */
    val ingredientItem = Item(ingredient)

    /**
     * The ingredient's name.
     */
    val ingredientName = itemName(ingredient)
}
