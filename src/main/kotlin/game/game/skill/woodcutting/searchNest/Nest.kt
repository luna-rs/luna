package game.skill.woodcutting.searchNest

import api.predef.*
import io.luna.game.model.item.Item

/**
 * An enumerated type representing all nests that can be searched.
 *
 * @author lare96
 */
enum class Nest(val id: Int, val weight: Int, private val weightedItems: List<Pair<Int, Int>>) {
    RED_EGG_NEST(id = 5070,
                 weight = 1,
                 weightedItems = listOf(5076 to 1)),
    GREEN_EGG_NEST(id = 5071,
                   weight = 1,
                   weightedItems = listOf(5078 to 1)),
    BLUE_EGG_NEST(id = 5072,
                  weight = 1,
                  weightedItems = listOf(5077 to 1)),
    SEEDS_NEST(id = 5073,
               weight = 65,
               weightedItems = listOf(5312 to 222, 5283 to 170, 5313 to 135, 5284 to 108, 5285 to 85, 5286 to 68,
                                      5314 to 54, 5287 to 42, 5288 to 34, 5315 to 27, 5289 to 22, 5290 to 17,
                                      5317 to 11, 5316 to 5)),
    RINGS_NEST(id = 5074,
               weight = 32,
               weightedItems = listOf(1637 to 40, 1635 to 35, 1639 to 15, 1641 to 9, 1643 to 1));

    /**
     * The items this nest can hold.
     */
    val items: List<Int> = weightedItems.map { it.first }

    companion object {

        /**
         * All values in this enum.
         */
        val VALUES = setOf(*values())

        /**
         * Nest ID -> Nest instance.
         */
        val NEST_MAP = values().associateBy { it.id }

        /**
         * Determines if [id] is a nest.
         *
         * @param id The id of the item to check.
         */
        fun isNest(id: Int) = NEST_MAP.containsKey(id)

        /**
         * Picks the nest that falls out of a tree, by [weight].
         */
        fun roll(): Nest = weighted(VALUES.map { it to it.weight })

        private fun <T> weighted(entries: List<Pair<T, Int>>): T {
            var roll = rand().nextInt(entries.sumOf { it.second })
            for ((value, weight) in entries) {
                roll -= weight
                if (roll < 0) {
                    return value
                }
            }
            throw IllegalStateException("Weights must be positive.")
        }
    }

    /**
     * Picks a random item from this nest, by weight.
     */
    fun pickItem() = Item(weighted(weightedItems))
}