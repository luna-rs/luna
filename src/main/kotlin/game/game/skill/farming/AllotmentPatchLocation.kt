package game.skill.farming

/**
 * Data for allotment patches.
 * @author hydrozoa
 */
enum class AllotmentPatchLocation(val objectId: Int, val config: Int, val shifts: Int) {
    FALADOR_NW(8550, Farming.ALLOTMENT_VARP_1, 0),
    FALADOR_SE(8551, Farming.ALLOTMENT_VARP_1, 8),
    CATHERBY_NORTH(8552, Farming.ALLOTMENT_VARP_1, 16),
    CATHERBY_SOUTH(8553, Farming.ALLOTMENT_VARP_1, 24),
    ARDOUGNE_NORTH(8554, Farming.ALLOTMENT_VARP_2, 0),
    ARDOUGNE_SOUTH(8555, Farming.ALLOTMENT_VARP_2, 8),
    PORT_PHASMATYS_NORTH(8556, Farming.ALLOTMENT_VARP_2, 16),
    PORT_PHASMATYS_SOUTH(8557, Farming.ALLOTMENT_VARP_2, 24),
    ;

    companion object {
        private val locations = AllotmentPatchLocation.values().associateBy(AllotmentPatchLocation::objectId)
        fun lookup(objectId: Int): AllotmentPatchLocation? = locations[objectId]
    }
}