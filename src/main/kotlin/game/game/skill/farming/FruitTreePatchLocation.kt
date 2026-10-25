package game.skill.farming

import game.skill.farming.patch.*

/**
 * Data for fruit tree patches. Every patch is drawn from one byte of [Farming.FRUIT_TREE_PATCH_VARP] (varbits 704 to
 * 707).
 *
 * @author hydrozoa
 */
enum class FruitTreePatchLocation(override val objectId: Int, override val shifts: Int) : PatchLocation {
    GNOME_STRONGHOLD(7962, 0),
    GNOME_VILLAGE(7963, 8),
    BRIMHAVEN(7964, 16),
    CATHERBY(7965, 24),
    ;

    override val varpId = Farming.FRUIT_TREE_PATCH_VARP

    override val key = "fruit_tree_${name.lowercase()}"

    override fun createPatch() = FruitTreePatch(this)

    companion object {
        private val locations = FruitTreePatchLocation.values().associateBy(FruitTreePatchLocation::objectId)
        fun lookup(objectId: Int): FruitTreePatchLocation? = locations[objectId]
    }
}
