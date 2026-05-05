package game.skill.farming

/**
 * Data for allotment patches.
 * @author hydrozoa
 */
enum class AllotmentPatchLocation(val objectId: Int, val config: Int, val shifts: Int) {
    FALADOR_SE(8551, 504, 8),
    FALADOR_NW(8550, 504, 0),
    CATHERBY(0, 0, 0), // todo
    ARDOUGNE(0, 0,0),
    PORT_PHATASMYS(0, 0, 0),
    ;

    companion object {
        private val locations = AllotmentPatchLocation.values().associateBy(AllotmentPatchLocation::objectId)
        fun lookup(objectId: Int): AllotmentPatchLocation? = locations[objectId]
    }
}