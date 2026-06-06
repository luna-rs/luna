package game.skill.crafting.jewelleryMaking

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap

/**
 * An enum representing the gold jewellery table.
 *
 * @author lare96
 */
enum class GoldJewelleryTable(val baseName: String,
                              val buttonWidgetId: Int,
                              val mouldWidgetId: Int,
                              val mouldId: Int,
                              val jewelleryItems: List<JewelleryItem>) {
    RINGS(baseName = "ring",
          buttonWidgetId = 4233,
          mouldWidgetId = 4229,
          mouldId = 1592,
          jewelleryItems = listOf(
              JewelleryItem(1635, 5, 15.0),
              JewelleryItem(1637, 20, 40.0, "Sapphire"),
              JewelleryItem(1639, 27, 55.0, "Emerald"),
              JewelleryItem(1641, 34, 70.0, "Ruby"),
              JewelleryItem(1643, 43, 85.0, "Diamond"),
              JewelleryItem(1645, 55, 100.0, "Dragonstone"),
              JewelleryItem(6575, 67, 115.0, "Onyx"),
          )),
    NECKLACES(baseName = "necklace",
              buttonWidgetId = 4239,
              mouldWidgetId = 4235,
              mouldId = 1597,
              jewelleryItems = listOf(
                  JewelleryItem(1654, 6, 20.0),
                  JewelleryItem(1656, 22, 55.0, "Sapphire"),
                  JewelleryItem(1658, 29, 60.0, "Emerald"),
                  JewelleryItem(1660, 40, 75.0, "Ruby"),
                  JewelleryItem(1662, 56, 90.0, "Diamond"),
                  JewelleryItem(1664, 72, 105.0, "Dragonstone"),
              )),
    AMULETS(baseName = "amulet",
            buttonWidgetId = 4245,
            mouldWidgetId = 4241,
            mouldId = 1595,
            jewelleryItems = listOf(
                JewelleryItem(1673, 8, 30.0),
                JewelleryItem(1675, 24, 65.0, "Sapphire"),
                JewelleryItem(1677, 31, 70.0, "Emerald"),
                JewelleryItem(1679, 50, 85.0, "Ruby"),
                JewelleryItem(1681, 70, 100.0, "Diamond"),
                JewelleryItem(1683, 80, 150.0, "Dragonstone"),
                JewelleryItem(6579, 90, 165.0, "Onyx"),
            ));

    companion object {

        /**
         * An immutable set of all mould ids that create gold jewellery.
         */
        val MOULDS = values().map { it.mouldId }.toSet()

        /**
         * An immutable list of [values].
         */
        val VALUES = ImmutableList.copyOf(values())

        /**
         * An immutable map of jewellery ids to [JewelleryItem] types.
         */
        val ID_TO_JEWELLERY = run {
            val map = ImmutableMap.builder<Int, JewelleryItem>()
            for(table in values()) {
                for(jewellery in table.jewelleryItems) {
                    map.put(jewellery.item.id, jewellery)
                }
            }
            return@run map.build()
        }
    }

    /**
     * The text widget id for this table.
     */
    val textWidgetId = mouldWidgetId + 1
}