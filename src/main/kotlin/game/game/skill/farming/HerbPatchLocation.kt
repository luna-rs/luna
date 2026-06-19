package game.skill.farming

/**
 * Data for herb patches.
 * @author hydrozoa
 */
enum class HerbPatchLocation(val objectId: Int, val shifts: Int) {
    FALADOR(8150, 0),
    CATHERBY(8151, 8),
    ARDOUGNE(8152, 16),
    PORT_PHATASMYS(8153, 24),
    ;

    companion object {
        private val locations = HerbPatchLocation.values().associateBy(HerbPatchLocation::objectId)
        fun lookup(objectId: Int): HerbPatchLocation? = locations[objectId]
    }
}