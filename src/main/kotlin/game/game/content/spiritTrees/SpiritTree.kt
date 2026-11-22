package game.content.spiritTrees

import io.luna.game.model.Position

/**
 * A place the spirit trees can send players to.
 *
 * @property objectId The spirit tree's Talk-to object.
 * @property landing Where players arrive.
 * @property option What the old trees call this place.
 * @author TheLining
 */
enum class SpiritTree(val objectId: Int, val landing: Position, val option: String) {
    TREE_GNOME_VILLAGE(SpiritTrees.VILLAGE_TREE, Position(2542, 3169), "Tree Gnome Village."),
    GNOME_STRONGHOLD(SpiritTrees.STRONGHOLD_TREE, Position(2461, 3444), "Gnome stronghold."),
    BATTLEFIELD_OF_KHAZARD(SpiritTrees.YOUNG_TREE, Position(2555, 3259), "Battlefield of Khazard."),
    VARROCK(SpiritTrees.YOUNG_TREE, Position(3179, 3507), "Forest north of Varrock.");

    /**
     * The destinations this tree offers, in dialogue order.
     */
    val destinations: List<SpiritTree>
        get() = when (this) {
            TREE_GNOME_VILLAGE -> listOf(BATTLEFIELD_OF_KHAZARD, VARROCK, GNOME_STRONGHOLD)
            GNOME_STRONGHOLD -> listOf(TREE_GNOME_VILLAGE, VARROCK, BATTLEFIELD_OF_KHAZARD)
            BATTLEFIELD_OF_KHAZARD, VARROCK -> listOf(SpiritTrees.youngDestination)
        }
}
