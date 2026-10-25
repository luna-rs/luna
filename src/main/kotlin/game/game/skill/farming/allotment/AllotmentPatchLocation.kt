package game.skill.farming.allotment

import game.skill.farming.*
import game.skill.farming.flower.*
import game.skill.farming.patch.*

/**
 * Data for allotment patches. [flowerPatch] is the flower patch of the same farming area, which can protect the
 * allotment from disease.
 * @author hydrozoa
 */
enum class AllotmentPatchLocation(override val objectId: Int,
                                  override val varpId: Int,
                                  override val shifts: Int,
                                  val flowerPatch: FlowerPatchLocation) : PatchLocation {
    FALADOR_NW(8550, Farming.ALLOTMENT_VARP_1, 0, FlowerPatchLocation.FALADOR),
    FALADOR_SE(8551, Farming.ALLOTMENT_VARP_1, 8, FlowerPatchLocation.FALADOR),
    CATHERBY_NORTH(8552, Farming.ALLOTMENT_VARP_1, 16, FlowerPatchLocation.CATHERBY),
    CATHERBY_SOUTH(8553, Farming.ALLOTMENT_VARP_1, 24, FlowerPatchLocation.CATHERBY),
    ARDOUGNE_NORTH(8554, Farming.ALLOTMENT_VARP_2, 0, FlowerPatchLocation.ARDOUGNE),
    ARDOUGNE_SOUTH(8555, Farming.ALLOTMENT_VARP_2, 8, FlowerPatchLocation.ARDOUGNE),
    PORT_PHASMATYS_NORTH(8556, Farming.ALLOTMENT_VARP_2, 16, FlowerPatchLocation.PORT_PHASMATYS),
    PORT_PHASMATYS_SOUTH(8557, Farming.ALLOTMENT_VARP_2, 24, FlowerPatchLocation.PORT_PHASMATYS),
    ;

    override val key = "allotment_${name.lowercase()}"

    override fun createPatch() = AllotmentPatch(this)

    companion object {
        private val locations = AllotmentPatchLocation.values().associateBy(AllotmentPatchLocation::objectId)
        fun lookup(objectId: Int): AllotmentPatchLocation? = locations[objectId]
    }
}
