package game.skill.farming.flower

import game.skill.farming.*
import game.skill.farming.allotment.*
import game.skill.farming.patch.*

/**
 * Data for flower patches. Every patch is drawn from one byte of [Farming.FLOWER_PATCH_VARP] (varbits 728 to 731), and
 * sits between the two allotments of its farming area, see [AllotmentPatchLocation.flowerPatch].
 *
 * @author hydrozoa
 */
enum class FlowerPatchLocation(override val objectId: Int, override val shifts: Int) : PatchLocation {
    FALADOR(7847, 0),
    CATHERBY(7848, 8),
    ARDOUGNE(7849, 16),
    PORT_PHASMATYS(7850, 24),
    ;

    override val varpId = Farming.FLOWER_PATCH_VARP

    override val key = "flower_${name.lowercase()}"

    override fun createPatch() = FlowerPatch(this)

    companion object {
        private val locations = FlowerPatchLocation.values().associateBy(FlowerPatchLocation::objectId)
        fun lookup(objectId: Int): FlowerPatchLocation? = locations[objectId]
    }
}
