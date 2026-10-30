package game.skill.woodcutting.searchNest

import io.luna.game.model.item.Item
import io.luna.util.RandomUtils

/**
 * An enumerated type representing all nests that can be searched.
 *
 * @property weight How often this nest falls out of a tree, against the others. Nests that never do have 0.
 * @property amounts How many of an item this nest holds, for items that come more than one at a time.
 * @author lare96
 */
enum class Nest(val id: Int, val weight: Double, private val weightedItems: Map<Int, Double>,
                private val amounts: Map<Int, Int> = emptyMap()) {
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
               weightedItems = mapOf(1637 to 40.0, 1635 to 35.0, 1639 to 15.0, 1641 to 9.0, 1643 to 1.0)),

    // Only Wyson the gardener gives these out, for mole parts.
    WYSON_SEEDS_NEST(id = 7413,
                     weight = 0.0,
                     weightedItems = mapOf(5318 to 200.0, 5320 to 120.0, 5322 to 110.0, 5324 to 100.0,
                                           5319 to 100.0, 5100 to 100.0, 5323 to 90.0, 5321 to 90.0, 5312 to 30.0,
                                           5295 to 20.0, 5313 to 20.0, 5314 to 10.0, 5315 to 8.0, 5316 to 1.0,
                                           5317 to 1.0),
                     amounts = mapOf(5318 to 14, 5320 to 3, 5322 to 6, 5324 to 9, 5319 to 11, 5100 to 2, 5323 to 3,
                                     5321 to 2));

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
    fun pickItem(): Item {
        val id = RandomUtils.weightedRoll(weightedItems)
        return Item(id, amounts[id] ?: 1)
    }
}