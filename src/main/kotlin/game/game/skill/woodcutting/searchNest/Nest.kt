package game.skill.woodcutting.searchNest

import io.luna.game.model.item.Item
import io.luna.util.RandomUtils

/**
 * An enumerated type representing all nests that can be searched.
 *
 * @author lare96
 */
enum class Nest(val id: Int, val weight: Double, private val weightedItems: Map<Int, Double>) {
    RED_EGG_NEST(id = 5070,
                 weight = 1.0,
                 weightedItems = mapOf(5076 to 1.0)),
    GREEN_EGG_NEST(id = 5071,
                   weight = 1.0,
                   weightedItems = mapOf(5078 to 1.0)),
    BLUE_EGG_NEST(id = 5072,
                  weight = 1.0,
                  weightedItems = mapOf(5077 to 1.0)),
    SEEDS_NEST(id = 5073,
               weight = 65.0,
               weightedItems = mapOf(5312 to 222.0, 5283 to 170.0, 5313 to 135.0, 5284 to 108.0, 5285 to 85.0,
                                     5286 to 68.0, 5314 to 54.0, 5287 to 42.0, 5288 to 34.0, 5315 to 27.0,
                                     5289 to 22.0, 5290 to 17.0, 5317 to 11.0, 5316 to 5.0)),
    RINGS_NEST(id = 5074,
               weight = 32.0,
               weightedItems = mapOf(1637 to 40.0, 1635 to 35.0, 1639 to 15.0, 1641 to 9.0, 1643 to 1.0));

    /**
     * The items this nest can hold.
     */
    val items: List<Int> = weightedItems.keys.toList()

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
        fun roll(): Nest = RandomUtils.weightedRoll(VALUES.associateWith { it.weight })
    }

    /**
     * Picks a random item from this nest, by weight.
     */
    fun pickItem() = Item(RandomUtils.weightedRoll(weightedItems))
}