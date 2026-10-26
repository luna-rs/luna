package game.skill.farming.hops

import game.skill.farming.*
import game.skill.farming.patch.*

/**
 * Data for hops patches. Every patch is drawn from one byte of [Farming.HOPS_PATCH_VARP] (varbits 716 to 719). The map
 * places the same patch object on every tile of a hops patch, so every tile shares [objectId].
 *
 * @author hydrozoa
 */
enum class HopsPatchLocation(override val objectId: Int, override val shifts: Int) : PatchLocation {
    YANILLE(8173, 0),
    ENTRANA(8174, 8),
    LUMBRIDGE(8175, 16),
    MCGRUBORS_WOOD(8176, 24),
    ;

    override val varpId = Farming.HOPS_PATCH_VARP

    override val key = "hops_${name.lowercase()}"

    override fun createPatch() = HopsPatch(this)

    companion object {
        private val locations = HopsPatchLocation.values().associateBy(HopsPatchLocation::objectId)
        fun lookup(objectId: Int): HopsPatchLocation? = locations[objectId]
    }
}
