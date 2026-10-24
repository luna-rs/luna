package game.skill.farming

import game.skill.farming.patch.*

/**
 * Data for herb patches.
 * @author hydrozoa
 */
enum class HerbPatchLocation(override val objectId: Int, override val shifts: Int) : PatchLocation {
    FALADOR(8150, 0),
    CATHERBY(8151, 8),
    ARDOUGNE(8152, 16),
    PORT_PHATASMYS(8153, 24),
    ;

    override val varpId = Farming.HERB_PATCH_VARP

    override val key = "herb_${name.lowercase()}"

    override fun createPatch() = HerbPatch(this)

    companion object {
        private val locations = HerbPatchLocation.values().associateBy(HerbPatchLocation::objectId)
        fun lookup(objectId: Int): HerbPatchLocation? = locations[objectId]
    }
}
