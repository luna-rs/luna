package game.skill.smithing.smithBar

import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import game.skill.smithing.BarType

/**
 * Represents a table for every item that can be smithed using an anvil. Each table is split up by widget and index.
 *
 * @author lare96
 */
enum class SmithingTable(val widgetId: Int, val slotId: Int, val bars: Int, val items: List<SmithingItem>) {
    DAGGER(widgetId = 1119,
           slotId = 0,
           bars = 1,
           items = listOf(
               SmithingItem(level = 1, item = item("Bronze dagger"), barType = BarType.BRONZE),
               SmithingItem(level = 15, item = item("Iron dagger"), barType = BarType.IRON),
               SmithingItem(level = 30, item = item("Steel dagger"), barType = BarType.STEEL),
               SmithingItem(level = 50, item = item("Mithril dagger"), barType = BarType.MITHRIL),
               SmithingItem(level = 70, item = item("Adamant dagger"), barType = BarType.ADAMANT),
               SmithingItem(level = 85, item = item("Rune dagger"), barType = BarType.RUNE)
           )),
    AXE(widgetId = 1120,
        slotId = 0,
        bars = 1,
        items = listOf(
            SmithingItem(level = 1, item = item("Bronze axe"), barType = BarType.BRONZE),
            SmithingItem(level = 16, item = item("Iron axe"), barType = BarType.IRON),
            SmithingItem(level = 31, item = item("Steel axe"), barType = BarType.STEEL),
            SmithingItem(level = 51, item = item("Mithril axe"), barType = BarType.MITHRIL),
            SmithingItem(level = 71, item = item("Adamant axe"), barType = BarType.ADAMANT),
            SmithingItem(level = 86, item = item("Rune axe"), barType = BarType.RUNE)
        )),
    MACE(widgetId = 1120,
         slotId = 1,
         bars = 1,
         items = listOf(
             SmithingItem(level = 2, item = item("Bronze mace"), barType = BarType.BRONZE),
             SmithingItem(level = 17, item = item("Iron mace"), barType = BarType.IRON),
             SmithingItem(level = 32, item = item("Steel mace"), barType = BarType.STEEL),
             SmithingItem(level = 52, item = item("Mithril mace"), barType = BarType.MITHRIL),
             SmithingItem(level = 72, item = item("Adamant mace"), barType = BarType.ADAMANT),
             SmithingItem(level = 87, item = item("Rune mace"), barType = BarType.RUNE)
         )),
    MED_HELM(widgetId = 1122,
             slotId = 0,
             bars = 1,
             items = listOf(
                 SmithingItem(level = 3, item = item("Bronze med helm"), barType = BarType.BRONZE),
                 SmithingItem(level = 18, item = item("Iron med helm"), barType = BarType.IRON),
                 SmithingItem(level = 33, item = item("Steel med helm"), barType = BarType.STEEL),
                 SmithingItem(level = 53, item = item("Mithril med helm"), barType = BarType.MITHRIL),
                 SmithingItem(level = 73, item = item("Adamant med helm"), barType = BarType.ADAMANT),
                 SmithingItem(level = 88, item = item("Rune med helm"), barType = BarType.RUNE)
             )),
    SWORD(widgetId = 1119,
          slotId = 1,
          bars = 1,
          items = listOf(
              SmithingItem(level = 4, item = item("Bronze sword"), barType = BarType.BRONZE),
              SmithingItem(level = 19, item = item("Iron sword"), barType = BarType.IRON),
              SmithingItem(level = 34, item = item("Steel sword"), barType = BarType.STEEL),
              SmithingItem(level = 54, item = item("Mithril sword"), barType = BarType.MITHRIL),
              SmithingItem(level = 74, item = item("Adamant sword"), barType = BarType.ADAMANT),
              SmithingItem(level = 89, item = item("Rune sword"), barType = BarType.RUNE)
          )),
    DART_TIP(widgetId = 1123,
             slotId = 0,
             bars = 1,
             items = listOf(
                 SmithingItem(level = 4, item = item("Bronze dart tip", 10), barType = BarType.BRONZE),
                 SmithingItem(level = 19, item = item("Iron dart tip", 10), barType = BarType.IRON),
                 SmithingItem(level = 34, item = item("Steel dart tip", 10), barType = BarType.STEEL),
                 SmithingItem(level = 54, item = item("Mithril dart tip", 10), barType = BarType.MITHRIL),
                 SmithingItem(level = 74, item = item("Adamant dart tip", 10), barType = BarType.ADAMANT),
                 SmithingItem(level = 89, item = item("Rune dart tip", 10), barType = BarType.RUNE)
             )),
    NAILS(widgetId = 1122,
          slotId = 4,
          bars = 1,
          items = listOf(
              SmithingItem(level = 4, item = item("Bronze nails", 15), barType = BarType.BRONZE),
              SmithingItem(level = 19, item = item("Iron nails", 15), barType = BarType.IRON),
              SmithingItem(level = 34, item = item("Steel nails", 15), barType = BarType.STEEL),
              SmithingItem(level = 54, item = item("Mithril nails", 15), barType = BarType.MITHRIL),
              SmithingItem(level = 74, item = item("Adamantite nails", 15), barType = BarType.ADAMANT),
              SmithingItem(level = 89, item = item("Rune nails", 15), barType = BarType.RUNE)
          )),
    SCIMITAR(widgetId = 1119,
             slotId = 2,
             bars = 2,
             items = listOf(
                 SmithingItem(level = 5, item = item("Bronze scimitar"), barType = BarType.BRONZE),
                 SmithingItem(level = 20, item = item("Iron scimitar"), barType = BarType.IRON),
                 SmithingItem(level = 35, item = item("Steel scimitar"), barType = BarType.STEEL),
                 SmithingItem(level = 55, item = item("Mithril scimitar"), barType = BarType.MITHRIL),
                 SmithingItem(level = 75, item = item("Adamant scimitar"), barType = BarType.ADAMANT),
                 SmithingItem(level = 90, item = item("Rune scimitar"), barType = BarType.RUNE)
             )),
    ARROWTIPS(widgetId = 1123,
              slotId = 1,
              bars = 1,
              items = listOf(
                  SmithingItem(level = 5, item = item("Bronze arrowtips", 15), barType = BarType.BRONZE),
                  SmithingItem(level = 20, item = item("Iron arrowtips", 15), barType = BarType.IRON),
                  SmithingItem(level = 35, item = item("Steel arrowtips", 15), barType = BarType.STEEL),
                  SmithingItem(level = 55, item = item("Mithril arrowtips", 15), barType = BarType.MITHRIL),
                  SmithingItem(level = 75, item = item("Adamant arrowtips", 15), barType = BarType.ADAMANT),
                  SmithingItem(level = 90, item = item("Rune arrowtips", 15), barType = BarType.RUNE)
              )),
    LONGSWORD(widgetId = 1119,
              slotId = 3,
              bars = 2,
              items = listOf(
                  SmithingItem(level = 6, item = item("Bronze longsword"), barType = BarType.BRONZE),
                  SmithingItem(level = 21, item = item("Iron longsword"), barType = BarType.IRON),
                  SmithingItem(level = 36, item = item("Steel longsword"), barType = BarType.STEEL),
                  SmithingItem(level = 56, item = item("Mithril longsword"), barType = BarType.MITHRIL),
                  SmithingItem(level = 76, item = item("Adamant longsword"), barType = BarType.ADAMANT),
                  SmithingItem(level = 91, item = item("Rune longsword"), barType = BarType.RUNE)
              )),
    FULL_HELM(widgetId = 1122,
              slotId = 1,
              bars = 2,
              items = listOf(
                  SmithingItem(level = 7, item = item("Bronze full helm"), barType = BarType.BRONZE),
                  SmithingItem(level = 22, item = item("Iron full helm"), barType = BarType.IRON),
                  SmithingItem(level = 37, item = item("Steel full helm"), barType = BarType.STEEL),
                  SmithingItem(level = 57, item = item("Mithril full helm"), barType = BarType.MITHRIL),
                  SmithingItem(level = 77, item = item("Adamant full helm"), barType = BarType.ADAMANT),
                  SmithingItem(level = 92, item = item("Rune full helm"), barType = BarType.RUNE)
              )),
    THROWING_KNIVES(widgetId = 1123,
                    slotId = 2,
                    bars = 1,
                    items = listOf(
                        SmithingItem(level = 7, item = item("Bronze knife", 5), barType = BarType.BRONZE),
                        SmithingItem(level = 22, item = item("Iron knife", 5), barType = BarType.IRON),
                        SmithingItem(level = 37, item = item("Steel knife", 5), barType = BarType.STEEL),
                        SmithingItem(level = 57, item = item("Mithril knife", 5), barType = BarType.MITHRIL),
                        SmithingItem(level = 77, item = item("Adamant knife", 5), barType = BarType.ADAMANT),
                        SmithingItem(level = 92, item = item("Rune knife", 5), barType = BarType.RUNE)
                    )),
    SQ_SHIELD(widgetId = 1122,
              slotId = 2,
              bars = 2,
              items = listOf(
                  SmithingItem(level = 8, item = item("Bronze sq shield"), barType = BarType.BRONZE),
                  SmithingItem(level = 23, item = item("Iron sq shield"), barType = BarType.IRON),
                  SmithingItem(level = 38, item = item("Steel sq shield"), barType = BarType.STEEL),
                  SmithingItem(level = 58, item = item("Mithril sq shield"), barType = BarType.MITHRIL),
                  SmithingItem(level = 78, item = item("Adamant sq shield"), barType = BarType.ADAMANT),
                  SmithingItem(level = 93, item = item("Rune sq shield"), barType = BarType.RUNE)
              )),
    WARHAMMER(widgetId = 1120,
              slotId = 2,
              bars = 3,
              items = listOf(
                  SmithingItem(level = 9, item = item("Bronze warhammer"), barType = BarType.BRONZE),
                  SmithingItem(level = 24, item = item("Iron warhammer"), barType = BarType.IRON),
                  SmithingItem(level = 39, item = item("Steel warhammer"), barType = BarType.STEEL),
                  SmithingItem(level = 59, item = item("Mithril warhammer"), barType = BarType.MITHRIL),
                  SmithingItem(level = 79, item = item("Addy warhammer"), barType = BarType.ADAMANT),
                  SmithingItem(level = 94, item = item("Rune warhammer"), barType = BarType.RUNE)
              )),
    BATTLEAXE(widgetId = 1120,
              slotId = 3,
              bars = 3,
              items = listOf(
                  SmithingItem(level = 10, item = item("Bronze battleaxe"), barType = BarType.BRONZE),
                  SmithingItem(level = 25, item = item("Iron battleaxe"), barType = BarType.IRON),
                  SmithingItem(level = 40, item = item("Steel battleaxe"), barType = BarType.STEEL),
                  SmithingItem(level = 60, item = item("Mithril battleaxe"), barType = BarType.MITHRIL),
                  SmithingItem(level = 80, item = item("Adamant battleaxe"), barType = BarType.ADAMANT),
                  SmithingItem(level = 95, item = item("Rune battleaxe"), barType = BarType.RUNE)
              )),
    CHAINBODY(widgetId = 1121,
              slotId = 0,
              bars = 3,
              items = listOf(
                  SmithingItem(level = 11, item = item("Bronze chainbody"), barType = BarType.BRONZE),
                  SmithingItem(level = 26, item = item("Iron chainbody"), barType = BarType.IRON),
                  SmithingItem(level = 41, item = item("Steel chainbody"), barType = BarType.STEEL),
                  SmithingItem(level = 61, item = item("Mithril chainbody"), barType = BarType.MITHRIL),
                  SmithingItem(level = 81, item = item("Adamant chainbody"), barType = BarType.ADAMANT),
                  SmithingItem(level = 96, item = item("Rune chainbody"), barType = BarType.RUNE)
              )),
    KITESHIELD(widgetId = 1122,
               slotId = 3,
               bars = 3,
               items = listOf(
                   SmithingItem(level = 12, item = item("Bronze kiteshield"), barType = BarType.BRONZE),
                   SmithingItem(level = 27, item = item("Iron kiteshield"), barType = BarType.IRON),
                   SmithingItem(level = 42, item = item("Steel kiteshield"), barType = BarType.STEEL),
                   SmithingItem(level = 62, item = item("Mithril kiteshield"), barType = BarType.MITHRIL),
                   SmithingItem(level = 82, item = item("Adamant kiteshield"), barType = BarType.ADAMANT),
                   SmithingItem(level = 97, item = item("Rune kiteshield"), barType = BarType.RUNE)
               )),
    LANTERN_FRAME(widgetId = 1121,
                  slotId = 4,
                  bars = 1,
                  items = listOf(
                      SmithingItem(level = 26, item = item("Oil lantern frame"), barType = BarType.IRON),
                      SmithingItem(level = 49, item = item("Bullseye lantern"), barType = BarType.STEEL)
                  )),
    STUDS(widgetId = 1123,
          slotId = 4,
          bars = 1,
          items = listOf(
              SmithingItem(level = 36, item = item("Steel studs"), barType = BarType.STEEL)
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
              SmithingItem(level = 13, item = item("Bronze claws"), barType = BarType.BRONZE),
              SmithingItem(level = 28, item = item("Iron claws"), barType = BarType.IRON),
              SmithingItem(level = 43, item = item("Steel claws"), barType = BarType.STEEL),
              SmithingItem(level = 63, item = item("Mithril claws"), barType = BarType.MITHRIL),
              SmithingItem(level = 83, item = item("Adamant claws"), barType = BarType.ADAMANT),
              SmithingItem(level = 98, item = item("Rune claws"), barType = BarType.RUNE)
          )),
    TWO_HANDED_SWORD(widgetId = 1119,
                     slotId = 4,
                     bars = 3,
                     items = listOf(
                         SmithingItem(level = 14, item = item("Bronze 2h sword"), barType = BarType.BRONZE),
                         SmithingItem(level = 29, item = item("Iron 2h sword"), barType = BarType.IRON),
                         SmithingItem(level = 44, item = item("Steel 2h sword"), barType = BarType.STEEL),
                         SmithingItem(level = 64, item = item("Mithril 2h sword"), barType = BarType.MITHRIL),
                         SmithingItem(level = 84, item = item("Adamant 2h sword"), barType = BarType.ADAMANT),
                         SmithingItem(level = 99, item = item("Rune 2h sword"), barType = BarType.RUNE)
                     )),
    PLATELEGS(widgetId = 1121,
              slotId = 1,
              bars = 3,
              items = listOf(
                  SmithingItem(level = 16, item = item("Bronze platelegs"), barType = BarType.BRONZE),
                  SmithingItem(level = 31, item = item("Iron platelegs"), barType = BarType.IRON),
                  SmithingItem(level = 46, item = item("Steel platelegs"), barType = BarType.STEEL),
                  SmithingItem(level = 66, item = item("Mithril platelegs"), barType = BarType.MITHRIL),
                  SmithingItem(level = 86, item = item("Adamant platelegs"), barType = BarType.ADAMANT),
                  SmithingItem(level = 99, item = item("Rune platelegs"), barType = BarType.RUNE)
              )),
    PLATESKIRT(widgetId = 1121,
               slotId = 2,
               bars = 3,
               items = listOf(
                   SmithingItem(level = 16, item = item("Bronze plateskirt"), barType = BarType.BRONZE),
                   SmithingItem(level = 31, item = item("Iron plateskirt"), barType = BarType.IRON),
                   SmithingItem(level = 46, item = item("Steel plateskirt"), barType = BarType.STEEL),
                   SmithingItem(level = 66, item = item("Mithril plateskirt"), barType = BarType.MITHRIL),
                   SmithingItem(level = 86, item = item("Adamant plateskirt"), barType = BarType.ADAMANT),
                   SmithingItem(level = 99, item = item("Rune plateskirt"), barType = BarType.RUNE)
               )),
    PLATEBODY(widgetId = 1121,
              slotId = 3,
              bars = 5,
              items = listOf(
                  SmithingItem(level = 18, item = item("Bronze platebody"), barType = BarType.BRONZE),
                  SmithingItem(level = 33, item = item("Iron platebody"), barType = BarType.IRON),
                  SmithingItem(level = 48, item = item("Steel platebody"), barType = BarType.STEEL),
                  SmithingItem(level = 68, item = item("Mithril platebody"), barType = BarType.MITHRIL),
                  SmithingItem(level = 88, item = item("Adamant platebody"), barType = BarType.ADAMANT),
                  SmithingItem(level = 99, item = item("Rune platebody"), barType = BarType.RUNE)
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