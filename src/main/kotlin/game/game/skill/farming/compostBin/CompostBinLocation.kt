package game.skill.farming.compostBin

import game.skill.farming.*

/**
 * Data for compost bins. Every bin is drawn from one byte of [Farming.COMPOST_BIN_VARP] (varbits 740 to 743).
 *
 * @author hydrozoa
 */
enum class CompostBinLocation(val objectId: Int, val shifts: Int) {
    FALADOR(7836, 0),
    CATHERBY(7837, 8),
    PORT_PHASMATYS(7838, 16),
    ARDOUGNE(7839, 24),
    ;

    /**
     * The id of the varp holding the state of this bin.
     */
    val varpId = Farming.COMPOST_BIN_VARP

    /**
     * The unique key this bin is saved under.
     */
    val key = "compost_bin_${name.lowercase()}"

    companion object {
        private val locations = CompostBinLocation.values().associateBy(CompostBinLocation::objectId)
        fun lookup(objectId: Int): CompostBinLocation? = locations[objectId]
    }
}
