package game.content.spiritTrees

import io.luna.game.model.Position

/**
 * A place the spirit trees can send players to.
 *
 * @property landing Where players arrive.
 * @property option What the old trees call this place.
 * @author TheLining
 */
enum class SpiritTree(val landing: Position, val option: String) {
    TREE_GNOME_VILLAGE(Position(2542, 3169), "Tree Gnome Village."),
    GNOME_STRONGHOLD(Position(2461, 3444), "Gnome stronghold."),
    BATTLEFIELD_OF_KHAZARD(Position(2555, 3259), "Battlefield of Khazard."),
    VARROCK(Position(3179, 3507), "Forest north of Varrock.")
}
