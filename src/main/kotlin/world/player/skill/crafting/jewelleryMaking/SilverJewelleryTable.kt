package world.player.skill.crafting.jewelleryMaking

import com.google.common.collect.ImmutableList
import world.player.skill.crafting.jewelleryMaking.GoldJewelleryTable.values

/**
 * An enum representing the silver jewellery table.
 *
 * @author lare96
 */
enum class SilverJewelleryTable(val mouldWidgetId: Int,
                                val mouldId: Int,
                                val jewelleryItem: JewelleryItem) {
    SARADOMIN_SYMBOL(mouldWidgetId = 15445,
                     mouldId = 1599,
                     jewelleryItem = JewelleryItem("Unstrung symbol", 16, 50.0)),
    SILVER_SICKLE(mouldWidgetId = 15459,
                  mouldId = 2976,
                  jewelleryItem = JewelleryItem("Silver sickle", 18, 50.0)),
    TIARA(mouldWidgetId = 15473,
          mouldId = 5523,
          jewelleryItem = JewelleryItem("Tiara", 23, 52.5)),
    DEMONIC_SIGIL(mouldWidgetId = 15481,
                  mouldId = 6747,
                  jewelleryItem = JewelleryItem("Demonic sigil", 30, 50.0)),
    ZAMORAK_SYMBOL(mouldWidgetId = 15452,
                   mouldId = 1594,
                   jewelleryItem = JewelleryItem("Unstrung emblem", 17, 50.0)),
    LIGHTNING_ROD(mouldWidgetId = 15466,
                  mouldId = 4200,
                  jewelleryItem = JewelleryItem("Conductor", 20, 50.0)),
    SILVTHRILL_ROD(mouldWidgetId = 18519,
                   mouldId = 7649,
                   jewelleryItem = JewelleryItem("Silvthrill rod", 25, 55.0));

    companion object {

        /**
         * An immutable set of all mould ids that create silver jewellery.
         */
        val MOULDS = values().map { it.mouldId }.toSet()

        /**
         * An immutable list of [values].
         */
        val VALUES = ImmutableList.copyOf(values())

        /**
         * An immutable map of jewellery ids to [JewelleryItem] types.
         */
        val ID_TO_TABLE = values().associateBy { it.jewelleryItem.item.id }
    }
}