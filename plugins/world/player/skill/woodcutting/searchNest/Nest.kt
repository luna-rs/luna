package world.player.skill.woodcutting.searchNest

import io.luna.game.model.item.Item

/**
 * An enumerated type representing all nests that can be searched.
 */
enum class Nest(val id: Int, val items: List<Int>) {
    RED_EGG_NEST(id = 5070,
                 items = listOf(5076)),
    GREEN_EGG_NEST(id = 5071,
                   items = listOf(5078)),
    BLUE_EGG_NEST(id = 5072,
                  items = listOf(5077)),
    SEEDS_NEST(id = 5073,
               items = listOf(5312, 5283, 5284, 5285, 5286, 5313, 5314, 5288, 5287, 5315, 5289, 5316, 5290, 5317)),
    RINGS_NEST(id = 5074,
               items = listOf(1635, 1637, 1639, 1641, 1643));

    companion object {

        val VALUES = setOf(*values())

        /**
         * Nest ID -> Nest instance.
         */
        val NEST_MAP = values().associateBy { it.id }
    }

    fun pickItem() = Item(items.random())

}