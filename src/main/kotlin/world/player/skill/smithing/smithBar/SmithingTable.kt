package world.player.skill.smithing.smithBar

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableMap
import io.luna.game.model.item.Item
import world.player.skill.smithing.BarType

/**
 * Represents a table for every item that can be smithed using an anvil. Each table is split up by widget and index.
 */
enum class SmithingTable(val widgetId: Int, val slotId: Int, val bars: Int, val items: List<SmithingItem>) {
    DAGGER(widgetId = 1119,
           slotId = 0,
           bars = 1,
           items = listOf(
               SmithingItem(level = 1, item = Item.byName("Bronze dagger"), barType = BarType.BRONZE),
               SmithingItem(level = 15, item = Item.byName("Iron dagger"), barType = BarType.IRON),
               SmithingItem(level = 30, item = Item.byName("Steel dagger"), barType = BarType.STEEL),
               SmithingItem(level = 50, item = Item.byName("Mithril dagger"), barType = BarType.MITHRIL),
               SmithingItem(level = 70, item = Item.byName("Adamant dagger"), barType = BarType.ADAMANT),
               SmithingItem(level = 85, item = Item.byName("Rune dagger"), barType = BarType.RUNE)
           )),
    AXE(widgetId = 1120,
        slotId = 0,
        bars = 1,
        items = listOf(
            SmithingItem(level = 1, item = Item.byName("Bronze axe"), barType = BarType.BRONZE),
            SmithingItem(level = 16, item = Item.byName("Iron axe"), barType = BarType.IRON),
            SmithingItem(level = 31, item = Item.byName("Steel axe"), barType = BarType.STEEL),
            SmithingItem(level = 51, item = Item.byName("Mithril axe"), barType = BarType.MITHRIL),
            SmithingItem(level = 71, item = Item.byName("Adamant axe"), barType = BarType.ADAMANT),
            SmithingItem(level = 86, item = Item.byName("Rune axe"), barType = BarType.RUNE)
        )),
    MACE(widgetId = 1120,
         slotId = 1,
         bars = 1,
         items = listOf(
             SmithingItem(level = 2, item = Item.byName("Bronze mace"), barType = BarType.BRONZE),
             SmithingItem(level = 17, item = Item.byName("Iron mace"), barType = BarType.IRON),
             SmithingItem(level = 32, item = Item.byName("Steel mace"), barType = BarType.STEEL),
             SmithingItem(level = 52, item = Item.byName("Mithril mace"), barType = BarType.MITHRIL),
             SmithingItem(level = 72, item = Item.byName("Adamant mace"), barType = BarType.ADAMANT),
             SmithingItem(level = 87, item = Item.byName("Rune mace"), barType = BarType.RUNE)
         )),
    MED_HELM(widgetId = 1122,
             slotId = 0,
             bars = 1,
             items = listOf(
                 SmithingItem(level = 3, item = Item.byName("Bronze med helm"), barType = BarType.BRONZE),
                 SmithingItem(level = 18, item = Item.byName("Iron med helm"), barType = BarType.IRON),
                 SmithingItem(level = 33, item = Item.byName("Steel med helm"), barType = BarType.STEEL),
                 SmithingItem(level = 53, item = Item.byName("Mithril med helm"), barType = BarType.MITHRIL),
                 SmithingItem(level = 73, item = Item.byName("Adamant med helm"), barType = BarType.ADAMANT),
                 SmithingItem(level = 88, item = Item.byName("Rune med helm"), barType = BarType.RUNE)
             )),
    SWORD(widgetId = 1119,
          slotId = 1,
          bars = 1,
          items = listOf(
              SmithingItem(level = 4, item = Item.byName("Bronze sword"), barType = BarType.BRONZE),
              SmithingItem(level = 19, item = Item.byName("Iron sword"), barType = BarType.IRON),
              SmithingItem(level = 34, item = Item.byName("Steel sword"), barType = BarType.STEEL),
              SmithingItem(level = 54, item = Item.byName("Mithril sword"), barType = BarType.MITHRIL),
              SmithingItem(level = 74, item = Item.byName("Adamant sword"), barType = BarType.ADAMANT),
              SmithingItem(level = 89, item = Item.byName("Rune sword"), barType = BarType.RUNE)
          )),
    DART_TIP(widgetId = 1123,
             slotId = 0,
             bars = 1,
             items = listOf(
                 SmithingItem(level = 4, item = Item.byName("Bronze dart tip", 10), barType = BarType.BRONZE),
                 SmithingItem(level = 19, item = Item.byName("Iron dart tip", 10), barType = BarType.IRON),
                 SmithingItem(level = 34, item = Item.byName("Steel dart tip", 10), barType = BarType.STEEL),
                 SmithingItem(level = 54, item = Item.byName("Mithril dart tip", 10), barType = BarType.MITHRIL),
                 SmithingItem(level = 74, item = Item.byName("Adamant dart tip", 10), barType = BarType.ADAMANT),
                 SmithingItem(level = 89, item = Item.byName("Rune dart tip", 10), barType = BarType.RUNE)
             )),
    NAILS(widgetId = 1122,
          slotId = 4,
          bars = 1,
          items = listOf(
              SmithingItem(level = 4, item = Item.byName("Bronze nails", 15), barType = BarType.BRONZE),
              SmithingItem(level = 19, item = Item.byName("Iron nails", 15), barType = BarType.IRON),
              SmithingItem(level = 34, item = Item.byName("Steel nails", 15), barType = BarType.STEEL),
              SmithingItem(level = 54, item = Item.byName("Mithril nails", 15), barType = BarType.MITHRIL),
              SmithingItem(level = 74, item = Item.byName("Adamantite nails", 15), barType = BarType.ADAMANT),
              SmithingItem(level = 89, item = Item.byName("Rune nails", 15), barType = BarType.RUNE)
          )),
    SCIMITAR(widgetId = 1119,
             slotId = 2,
             bars = 2,
             items = listOf(
                 SmithingItem(level = 5, item = Item.byName("Bronze scimitar"), barType = BarType.BRONZE),
                 SmithingItem(level = 20, item = Item.byName("Iron scimitar"), barType = BarType.IRON),
                 SmithingItem(level = 35, item = Item.byName("Steel scimitar"), barType = BarType.STEEL),
                 SmithingItem(level = 55, item = Item.byName("Mithril scimitar"), barType = BarType.MITHRIL),
                 SmithingItem(level = 75, item = Item.byName("Adamant scimitar"), barType = BarType.ADAMANT),
                 SmithingItem(level = 90, item = Item.byName("Rune scimitar"), barType = BarType.RUNE)
             )),
    ARROWTIPS(widgetId = 1123,
              slotId = 1,
              bars = 1,
              items = listOf(
                  SmithingItem(level = 5, item = Item.byName("Bronze arrowtips", 15), barType = BarType.BRONZE),
                  SmithingItem(level = 20, item = Item.byName("Iron arrowtips", 15), barType = BarType.IRON),
                  SmithingItem(level = 35, item = Item.byName("Steel arrowtips", 15), barType = BarType.STEEL),
                  SmithingItem(level = 55, item = Item.byName("Mithril arrowtips", 15), barType = BarType.MITHRIL),
                  SmithingItem(level = 75, item = Item.byName("Adamant arrowtips", 15), barType = BarType.ADAMANT),
                  SmithingItem(level = 90, item = Item.byName("Rune arrowtips", 15), barType = BarType.RUNE)
              )),
    LONGSWORD(widgetId = 1119,
              slotId = 3,
              bars = 2,
              items = listOf(
                  SmithingItem(level = 6, item = Item.byName("Bronze longsword"), barType = BarType.BRONZE),
                  SmithingItem(level = 21, item = Item.byName("Iron longsword"), barType = BarType.IRON),
                  SmithingItem(level = 36, item = Item.byName("Steel longsword"), barType = BarType.STEEL),
                  SmithingItem(level = 56, item = Item.byName("Mithril longsword"), barType = BarType.MITHRIL),
                  SmithingItem(level = 76, item = Item.byName("Adamant longsword"), barType = BarType.ADAMANT),
                  SmithingItem(level = 91, item = Item.byName("Rune longsword"), barType = BarType.RUNE)
              )),
    FULL_HELM(widgetId = 1122,
              slotId = 1,
              bars = 2,
              items = listOf(
                  SmithingItem(level = 7, item = Item.byName("Bronze full helm"), barType = BarType.BRONZE),
                  SmithingItem(level = 22, item = Item.byName("Iron full helm"), barType = BarType.IRON),
                  SmithingItem(level = 37, item = Item.byName("Steel full helm"), barType = BarType.STEEL),
                  SmithingItem(level = 57, item = Item.byName("Mithril full helm"), barType = BarType.MITHRIL),
                  SmithingItem(level = 77, item = Item.byName("Adamant full helm"), barType = BarType.ADAMANT),
                  SmithingItem(level = 92, item = Item.byName("Rune full helm"), barType = BarType.RUNE)
              )),
    THROWING_KNIVES(widgetId = 1123,
                    slotId = 2,
                    bars = 1,
                    items = listOf(
                        SmithingItem(level = 7, item = Item.byName("Bronze knife", 5), barType = BarType.BRONZE),
                        SmithingItem(level = 22, item = Item.byName("Iron knife", 5), barType = BarType.IRON),
                        SmithingItem(level = 37, item = Item.byName("Steel knife", 5), barType = BarType.STEEL),
                        SmithingItem(level = 57, item = Item.byName("Mithril knife", 5), barType = BarType.MITHRIL),
                        SmithingItem(level = 77, item = Item.byName("Adamant knife", 5), barType = BarType.ADAMANT),
                        SmithingItem(level = 92, item = Item.byName("Rune knife", 5), barType = BarType.RUNE)
                    )),
    SQ_SHIELD(widgetId = 1122,
              slotId = 2,
              bars = 2,
              items = listOf(
                  SmithingItem(level = 8, item = Item.byName("Bronze sq shield"), barType = BarType.BRONZE),
                  SmithingItem(level = 23, item = Item.byName("Iron sq shield"), barType = BarType.IRON),
                  SmithingItem(level = 38, item = Item.byName("Steel sq shield"), barType = BarType.STEEL),
                  SmithingItem(level = 58, item = Item.byName("Mithril sq shield"), barType = BarType.MITHRIL),
                  SmithingItem(level = 78, item = Item.byName("Adamant sq shield"), barType = BarType.ADAMANT),
                  SmithingItem(level = 93, item = Item.byName("Rune sq shield"), barType = BarType.RUNE)
              )),
    WARHAMMER(widgetId = 1120,
              slotId = 2,
              bars = 3,
              items = listOf(
                  SmithingItem(level = 9, item = Item.byName("Bronze warhammer"), barType = BarType.BRONZE),
                  SmithingItem(level = 24, item = Item.byName("Iron warhammer"), barType = BarType.IRON),
                  SmithingItem(level = 39, item = Item.byName("Steel warhammer"), barType = BarType.STEEL),
                  SmithingItem(level = 59, item = Item.byName("Mithril warhammer"), barType = BarType.MITHRIL),
                  SmithingItem(level = 79, item = Item.byName("Addy warhammer"), barType = BarType.ADAMANT),
                  SmithingItem(level = 94, item = Item.byName("Rune warhammer"), barType = BarType.RUNE)
              )),
    BATTLEAXE(widgetId = 1120,
              slotId = 3,
              bars = 3,
              items = listOf(
                  SmithingItem(level = 10, item = Item.byName("Bronze battleaxe"), barType = BarType.BRONZE),
                  SmithingItem(level = 25, item = Item.byName("Iron battleaxe"), barType = BarType.IRON),
                  SmithingItem(level = 40, item = Item.byName("Steel battleaxe"), barType = BarType.STEEL),
                  SmithingItem(level = 60, item = Item.byName("Mithril battleaxe"), barType = BarType.MITHRIL),
                  SmithingItem(level = 80, item = Item.byName("Adamant battleaxe"), barType = BarType.ADAMANT),
                  SmithingItem(level = 95, item = Item.byName("Rune battleaxe"), barType = BarType.RUNE)
              )),
    CHAINBODY(widgetId = 1121,
              slotId = 0,
              bars = 3,
              items = listOf(
                  SmithingItem(level = 11, item = Item.byName("Bronze chainbody"), barType = BarType.BRONZE),
                  SmithingItem(level = 26, item = Item.byName("Iron chainbody"), barType = BarType.IRON),
                  SmithingItem(level = 41, item = Item.byName("Steel chainbody"), barType = BarType.STEEL),
                  SmithingItem(level = 61, item = Item.byName("Mithril chainbody"), barType = BarType.MITHRIL),
                  SmithingItem(level = 81, item = Item.byName("Adamant chainbody"), barType = BarType.ADAMANT),
                  SmithingItem(level = 96, item = Item.byName("Rune chainbody"), barType = BarType.RUNE)
              )),
    KITESHIELD(widgetId = 1122,
               slotId = 3,
               bars = 3,
               items = listOf(
                   SmithingItem(level = 12, item = Item.byName("Bronze kiteshield"), barType = BarType.BRONZE),
                   SmithingItem(level = 27, item = Item.byName("Iron kiteshield"), barType = BarType.IRON),
                   SmithingItem(level = 42, item = Item.byName("Steel kiteshield"), barType = BarType.STEEL),
                   SmithingItem(level = 62, item = Item.byName("Mithril kiteshield"), barType = BarType.MITHRIL),
                   SmithingItem(level = 82, item = Item.byName("Adamant kiteshield"), barType = BarType.ADAMANT),
                   SmithingItem(level = 97, item = Item.byName("Rune kiteshield"), barType = BarType.RUNE)
               )),
    LANTERN_FRAME(widgetId = 1121,
                  slotId = 4,
                  bars = 1,
                  items = listOf(
                      SmithingItem(level = 26, item = Item.byName("Oil lantern frame"), barType = BarType.IRON),
                      SmithingItem(level = 49, item = Item.byName("Bullseye lantern"), barType = BarType.STEEL)
                  )),
    STUDS(widgetId = 1123,
          slotId = 4,
          bars = 1,
          items = listOf(
              SmithingItem(level = 36, item = Item.byName("Steel studs"), barType = BarType.STEEL)
          )),

    // Unused, but custom items could still be displayed here.
    OTHER(widgetId = 1123,
          slotId = 3,
          bars = 0,
          items = emptyList()),
    CLAWS(widgetId = 1120,
          slotId = 4,
          bars = 2,
          items = listOf(
              SmithingItem(level = 13, item = Item.byName("Bronze claws"), barType = BarType.BRONZE),
              SmithingItem(level = 28, item = Item.byName("Iron claws"), barType = BarType.IRON),
              SmithingItem(level = 43, item = Item.byName("Steel claws"), barType = BarType.STEEL),
              SmithingItem(level = 63, item = Item.byName("Mithril claws"), barType = BarType.MITHRIL),
              SmithingItem(level = 83, item = Item.byName("Adamant claws"), barType = BarType.ADAMANT),
              SmithingItem(level = 98, item = Item.byName("Rune claws"), barType = BarType.RUNE)
          )),
    TWO_HANDED_SWORD(widgetId = 1119,
                     slotId = 4,
                     bars = 3,
                     items = listOf(
                         SmithingItem(level = 14, item = Item.byName("Bronze 2h sword"), barType = BarType.BRONZE),
                         SmithingItem(level = 29, item = Item.byName("Iron 2h sword"), barType = BarType.IRON),
                         SmithingItem(level = 44, item = Item.byName("Steel 2h sword"), barType = BarType.STEEL),
                         SmithingItem(level = 64, item = Item.byName("Mithril 2h sword"), barType = BarType.MITHRIL),
                         SmithingItem(level = 84, item = Item.byName("Adamant 2h sword"), barType = BarType.ADAMANT),
                         SmithingItem(level = 99, item = Item.byName("Rune 2h sword"), barType = BarType.RUNE)
                     )),
    PLATELEGS(widgetId = 1121,
              slotId = 1,
              bars = 3,
              items = listOf(
                  SmithingItem(level = 16, item = Item.byName("Bronze platelegs"), barType = BarType.BRONZE),
                  SmithingItem(level = 31, item = Item.byName("Iron platelegs"), barType = BarType.IRON),
                  SmithingItem(level = 46, item = Item.byName("Steel platelegs"), barType = BarType.STEEL),
                  SmithingItem(level = 66, item = Item.byName("Mithril platelegs"), barType = BarType.MITHRIL),
                  SmithingItem(level = 86, item = Item.byName("Adamant platelegs"), barType = BarType.ADAMANT),
                  SmithingItem(level = 99, item = Item.byName("Rune platelegs"), barType = BarType.RUNE)
              )),
    PLATESKIRT(widgetId = 1121,
               slotId = 2,
               bars = 3,
               items = listOf(
                   SmithingItem(level = 16, item = Item.byName("Bronze plateskirt"), barType = BarType.BRONZE),
                   SmithingItem(level = 31, item = Item.byName("Iron plateskirt"), barType = BarType.IRON),
                   SmithingItem(level = 46, item = Item.byName("Steel plateskirt"), barType = BarType.STEEL),
                   SmithingItem(level = 66, item = Item.byName("Mithril plateskirt"), barType = BarType.MITHRIL),
                   SmithingItem(level = 86, item = Item.byName("Adamant plateskirt"), barType = BarType.ADAMANT),
                   SmithingItem(level = 99, item = Item.byName("Rune plateskirt"), barType = BarType.RUNE)
               )),
    PLATEBODY(widgetId = 1121,
              slotId = 3,
              bars = 5,
              items = listOf(
                  SmithingItem(level = 18, item = Item.byName("Bronze platebody"), barType = BarType.BRONZE),
                  SmithingItem(level = 33, item = Item.byName("Iron platebody"), barType = BarType.IRON),
                  SmithingItem(level = 48, item = Item.byName("Steel platebody"), barType = BarType.STEEL),
                  SmithingItem(level = 68, item = Item.byName("Mithril platebody"), barType = BarType.MITHRIL),
                  SmithingItem(level = 88, item = Item.byName("Adamant platebody"), barType = BarType.ADAMANT),
                  SmithingItem(level = 99, item = Item.byName("Rune platebody"), barType = BarType.RUNE)
              ));

    companion object {

        /**
         * Immutable mappings of [SmithingItem.item] to [SmithingItem].
         */
        val ID_TO_ITEM = run {
            val map = ImmutableMap.builder<Int, SmithingItem>()
            for (table in values()) {
                for (smithingItem in table.items) {
                    map.put(smithingItem.item.id, smithingItem)
                }
            }
            map.build()
        }

        /**
         * Immutable mappings of [SmithingItem.item] to [SmithingTable].
         */
        val ID_TO_TABLE = run {
            val map = ImmutableMap.builder<Int, SmithingTable>()
            for (table in values()) {
                for (smithingItem in table.items) {
                    map.put(smithingItem.item.id, table)
                }
            }
            map.build()
        }

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableList.copyOf(values())
    }
}