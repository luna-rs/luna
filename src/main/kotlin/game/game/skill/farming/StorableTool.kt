package game.skill.farming

import game.skill.farming.Farming.storedTools
import io.luna.game.model.def.VarBitDefinition
import io.luna.game.model.mob.Player

/**
 * An enumerated type representing the tools that can be stored with a tool leprechaun. Each tool is stored in its own
 * varbit of [Farming.TOOLS_VARP], which the tool leprechaun interface reads to display what is stored.
 *
 * @author hydrozoa
 */
enum class StorableTool(val varbit: Int, val items: List<Int>, val max: Int, val slot: Int) {
    RAKE(varbit = 1435,
         items = listOf(5341),
         max = 1,
         slot = 0),
    SEED_DIBBER(varbit = 1436,
                items = listOf(5343),
                max = 1,
                slot = 1),
    SPADE(varbit = 1437,
          items = listOf(952),
          max = 1,
          slot = 2),
    SECATEURS(varbit = 1438,
              items = listOf(5329),
              max = 1,
              slot = 3),

    /**
     * The varbit holds the stored can's index in [items] plus one, so an empty can is stored as 1 and a can with 8 doses
     * of water as 9.
     */
    WATERING_CAN(varbit = 1439,
                 items = listOf(5331, 5333, 5334, 5335, 5336, 5337, 5338, 5339, 5340),
                 max = 1,
                 slot = 4),
    GARDENING_TROWEL(varbit = 1440,
                     items = listOf(5325),
                     max = 1,
                     slot = 5),
    BUCKET(varbit = 1441,
           items = listOf(1925),
           max = 31,
           slot = 0),
    COMPOST(varbit = 1442,
            items = listOf(6032),
            max = 255,
            slot = 1),
    SUPERCOMPOST(varbit = 1443,
                 items = listOf(6034),
                 max = 255,
                 slot = 2);

    companion object {

        /**
         * Tools that are stored one at a time, in interface slot order.
         */
        val SINGLE = listOf(RAKE, SEED_DIBBER, SPADE, SECATEURS, WATERING_CAN, GARDENING_TROWEL)

        /**
         * Tools that can be stored in bulk, in interface slot order.
         */
        val BULK = listOf(BUCKET, COMPOST, SUPERCOMPOST)
    }

    /**
     * The varbit definition, used to locate this tool's bits within [Farming.TOOLS_VARP].
     */
    private val def by lazy { VarBitDefinition.ALL.retrieve(varbit) }

    /**
     * The bit mask of this tool's value, before it is shifted into place.
     */
    private val mask by lazy { (1 shl (def.mostSignificantBit - def.leastSignificantBit + 1)) - 1 }

    /**
     * Returns the raw varbit value stored for this tool.
     */
    fun getValue(plr: Player): Int = (plr.storedTools shr def.leastSignificantBit) and mask

    /**
     * Sets the raw varbit value stored for this tool. Does not send the varp.
     */
    fun setValue(plr: Player, value: Int) {
        val lsb = def.leastSignificantBit
        plr.storedTools = (plr.storedTools and (mask shl lsb).inv()) or ((value and mask) shl lsb)
    }

    /**
     * Returns how many of this tool are stored.
     */
    fun getAmount(plr: Player): Int = if (this == WATERING_CAN) minOf(getValue(plr), 1) else getValue(plr)

    /**
     * Returns the item id displayed for this tool on the leprechaun interface, which is the stored watering can if
     * there is one.
     */
    fun getDisplayItem(plr: Player): Int {
        val value = getValue(plr)
        return if (this == WATERING_CAN && value > 0) items[value - 1] else items.first()
    }
}
