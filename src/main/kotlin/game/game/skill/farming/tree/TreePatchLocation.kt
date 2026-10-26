package game.skill.farming.tree

import game.skill.farming.*
import game.skill.farming.patch.*

/**
 * Data for tree patches. Every patch is drawn from one byte of [Farming.TREE_PATCH_VARP] (varbits 700 to 703).
 *
 * @author hydrozoa
 */
enum class TreePatchLocation(override val objectId: Int, override val shifts: Int) : PatchLocation {
    TAVERLEY(8388, 0),
    FALADOR(8389, 8),
    VARROCK(8390, 16),
    LUMBRIDGE(8391, 24),
    ;

    override val varpId = Farming.TREE_PATCH_VARP

    override val key = "tree_${name.lowercase()}"

    override fun createPatch() = TreePatch(this)

    companion object {
        private val locations = TreePatchLocation.values().associateBy(TreePatchLocation::objectId)
        fun lookup(objectId: Int): TreePatchLocation? = locations[objectId]
    }
}
