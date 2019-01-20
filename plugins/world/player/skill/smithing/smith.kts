/*import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation

/**
 * @author lare96 <http://github.org/lare96>
 */
// Try and represent this by Table Item index
private case class SmithItem(smithItem: Item, level: Int, bars: Int, lines: (Int, Int), typedef: Nothing, exp: Double)


private val ANIMATION = new Animation(898)

private val DAGGER = ???
private val AXE = ???
private val CHAINBODY = ???
private val MED_HELM = ???
private val DART_TIPS = ???
private val SWORD = ???
private val MACE = ???
private val PLATELEGS = ???
private val FULL_HELM = ???
private val ARROWTIPS = ???
private val SCIMITAR = ???
private val WARHAMMER = ???
private val PLATESKIRT = ???
private val SQ_SHIELD = ???
private val KNIVES = ???
private val LONGSWORD = ???
private val BATTLE_AXE = ???
private val PLATEBODY = ???
private val KITESHIELD = ???
private val WIRE = ???
private val TWO_HANDED_SWORD = ???
private val CLAWS = ???
private val OIL_FRAME_LANTERN = ???
private val NAILS = ???
private val STUDS = ???

private val BRONZE_BAR = 2349
private val BRONZE_EXP = 12.5
private val BRONZE_TABLE = Map(
  ??? -> SmithItem(new Item(1205), 1, 1, (1125, 1094), DAGGER), // Bronze dagger
  ??? -> SmithItem(new Item(1351), 1, 1, (1126, 1091), AXE), // Bronze axe
  ??? -> SmithItem(new Item(1422), 2, 1, (1129, 1093), MACE), // Bronze mace
  ??? -> SmithItem(new Item(1139), 3, 1, (1127, 1102), MED_HELM), // Bronze med helm
  ??? -> SmithItem(new Item(1277), 3, 1, (1128, 1085), SWORD), // Bronze sword
  ??? -> SmithItem(new Item(4819, 15), 4, 1, (13357, 13358), NAILS), // Bronze nails
  ??? -> SmithItem(new Item(819, 10), 4, 1, (1124, 1107), DART_TIPS), // Bronze dart tips
  ??? -> SmithItem(new Item(1794), 4, 1, (1132, 1096), WIRE), // Bronze wire
  ??? -> SmithItem(new Item(39, 15), 5, 1, (1130, 1108), ARROWTIPS), // Bronze arrowtips
  ??? -> SmithItem(new Item(1321), 5, 2, (1116, 1087), SCIMITAR), // Bronze scimitar
  ??? -> SmithItem(new Item(1291), 6, 2, (1089, 1086), LONGSWORD), // Bronze longsword
  ??? -> SmithItem(new Item(864, 5), 7, 1, (1131, 1106), KNIVES), // Bronze knives
  ??? -> SmithItem(new Item(1205), 7, 2, (1113, 1103), FULL_HELM), // Bronze full helm
  ??? -> SmithItem(new Item(1173), 8, 2, (1114, 1104), SQ_SHIELD), // Bronze sq. shield
  ??? -> SmithItem(new Item(1337), 9, 3, (1118, 1083), WARHAMMER), // Bronze warhammer
  ??? -> SmithItem(new Item(1375), 10, 3, (1095, 1092), BATTLE_AXE), // Bronze battleaxe
  ??? -> SmithItem(new Item(1103), 11, 3, (1109, 1098), CHAINBODY), // Bronze chainbody
  ??? -> SmithItem(new Item(1189), 12, 3, (1115, 1105), KITESHIELD), // Bronze kiteshield
  ??? -> SmithItem(new Item(1205), 13, 2, (8428, 8429), CLAWS), // Bronze claws
  ??? -> SmithItem(new Item(1205), 14, 3, (1090, 1088), TWO_HANDED_SWORD), // Bronze 2h sword
  ??? -> SmithItem(new Item(1075), 16, 3, (1110, 1099), PLATELEGS), // Bronze platelegs
  ??? -> SmithItem(new Item(1087), 16, 3, (1111, 1100), PLATESKIRT), // Bronze plateskirt
  ??? -> SmithItem(new Item(1117), 18, 5, (1112, 1101), PLATEBODY), // Bronze platebody
)

private val IRON_BAR = 2349
private val IRON_EXP = 12.5
private val IRON_TABLE = Map(
  ??? -> SmithItem(new Item(1205), 1, 1, (1125, 1094), DAGGER), // Bronze dagger
  ??? -> SmithItem(new Item(1351), 1, 1, (1126, 1091), AXE), // Bronze axe
  ??? -> SmithItem(new Item(1422), 2, 1, (1129, 1093), MACE), // Bronze mace
  ??? -> SmithItem(new Item(1139), 3, 1, (1127, 1102), MED_HELM), // Bronze med helm
  ??? -> SmithItem(new Item(1277), 3, 1, (1128, 1085), SWORD), // Bronze sword
  ??? -> SmithItem(new Item(4819, 15), 4, 1, (13357, 13358), NAILS), // Bronze nails
  ??? -> SmithItem(new Item(819, 10), 4, 1, (1124, 1107), DART_TIPS), // Bronze dart tips
  ??? -> SmithItem(new Item(1794), 4, 1, (1132, 1096), WIRE), // Bronze wire
  ??? -> SmithItem(new Item(39, 15), 5, 1, (1130, 1108), ARROWTIPS), // Bronze arrowtips
  ??? -> SmithItem(new Item(1321), 5, 2, (1116, 1087), SCIMITAR), // Bronze scimitar
  ??? -> SmithItem(new Item(1291), 6, 2, (1089, 1086), LONGSWORD), // Bronze longsword
  ??? -> SmithItem(new Item(864, 5), 7, 1, (1131, 1106), KNIVES), // Bronze knives
  ??? -> SmithItem(new Item(1205), 7, 2, (1113, 1103), FULL_HELM), // Bronze full helm
  ??? -> SmithItem(new Item(1173), 8, 2, (1114, 1104), SQ_SHIELD), // Bronze sq. shield
  ??? -> SmithItem(new Item(1337), 9, 3, (1118, 1083), WARHAMMER), // Bronze warhammer
  ??? -> SmithItem(new Item(1375), 10, 3, (1095, 1092), BATTLE_AXE), // Bronze battleaxe
  ??? -> SmithItem(new Item(1103), 11, 3, (1109, 1098), CHAINBODY), // Bronze chainbody
  ??? -> SmithItem(new Item(1189), 12, 3, (1115, 1105), KITESHIELD), // Bronze kiteshield
  ??? -> SmithItem(new Item(1205), 13, 2, (8428, 8429), CLAWS), // Bronze claws
  ??? -> SmithItem(new Item(1205), 14, 3, (1090, 1088), TWO_HANDED_SWORD), // Bronze 2h sword
  ??? -> SmithItem(new Item(1075), 16, 3, (1110, 1099), PLATELEGS), // Bronze platelegs
  ??? -> SmithItem(new Item(1087), 16, 3, (1111, 1100), PLATESKIRT), // Bronze plateskirt
  ??? -> SmithItem(new Item(1117), 18, 5, (1112, 1101), PLATEBODY), // Bronze platebody
)

private val STEEL_BAR = 2349
private val STEEL_EXP = 12.5
private val STEEL_TABLE = Map(
  ??? -> SmithItem(new Item(1205), 1, 1, (1125, 1094), DAGGER), // Bronze dagger
  ??? -> SmithItem(new Item(1351), 1, 1, (1126, 1091), AXE), // Bronze axe
  ??? -> SmithItem(new Item(1422), 2, 1, (1129, 1093), MACE), // Bronze mace
  ??? -> SmithItem(new Item(1139), 3, 1, (1127, 1102), MED_HELM), // Bronze med helm
  ??? -> SmithItem(new Item(1277), 3, 1, (1128, 1085), SWORD), // Bronze sword
  ??? -> SmithItem(new Item(4819, 15), 4, 1, (13357, 13358), NAILS), // Bronze nails
  ??? -> SmithItem(new Item(819, 10), 4, 1, (1124, 1107), DART_TIPS), // Bronze dart tips
  ??? -> SmithItem(new Item(1794), 4, 1, (1132, 1096), WIRE), // Bronze wire
  ??? -> SmithItem(new Item(39, 15), 5, 1, (1130, 1108), ARROWTIPS), // Bronze arrowtips
  ??? -> SmithItem(new Item(1321), 5, 2, (1116, 1087), SCIMITAR), // Bronze scimitar
  ??? -> SmithItem(new Item(1291), 6, 2, (1089, 1086), LONGSWORD), // Bronze longsword
  ??? -> SmithItem(new Item(864, 5), 7, 1, (1131, 1106), KNIVES), // Bronze knives
  ??? -> SmithItem(new Item(1205), 7, 2, (1113, 1103), FULL_HELM), // Bronze full helm
  ??? -> SmithItem(new Item(1173), 8, 2, (1114, 1104), SQ_SHIELD), // Bronze sq. shield
  ??? -> SmithItem(new Item(1337), 9, 3, (1118, 1083), WARHAMMER), // Bronze warhammer
  ??? -> SmithItem(new Item(1375), 10, 3, (1095, 1092), BATTLE_AXE), // Bronze battleaxe
  ??? -> SmithItem(new Item(1103), 11, 3, (1109, 1098), CHAINBODY), // Bronze chainbody
  ??? -> SmithItem(new Item(1189), 12, 3, (1115, 1105), KITESHIELD), // Bronze kiteshield
  ??? -> SmithItem(new Item(1205), 13, 2, (8428, 8429), CLAWS), // Bronze claws
  ??? -> SmithItem(new Item(1205), 14, 3, (1090, 1088), TWO_HANDED_SWORD), // Bronze 2h sword
  ??? -> SmithItem(new Item(1075), 16, 3, (1110, 1099), PLATELEGS), // Bronze platelegs
  ??? -> SmithItem(new Item(1087), 16, 3, (1111, 1100), PLATESKIRT), // Bronze plateskirt
  ??? -> SmithItem(new Item(1117), 18, 5, (1112, 1101), PLATEBODY), // Bronze platebody
)

private val MITHRIL_BAR = 2349
private val MITHRIL_EXP = 12.5
private val MITHRIL_TABLE = Map(
  ??? -> SmithItem(new Item(1205), 1, 1, (1125, 1094), DAGGER), // Bronze dagger
  ??? -> SmithItem(new Item(1351), 1, 1, (1126, 1091), AXE), // Bronze axe
  ??? -> SmithItem(new Item(1422), 2, 1, (1129, 1093), MACE), // Bronze mace
  ??? -> SmithItem(new Item(1139), 3, 1, (1127, 1102), MED_HELM), // Bronze med helm
  ??? -> SmithItem(new Item(1277), 3, 1, (1128, 1085), SWORD), // Bronze sword
  ??? -> SmithItem(new Item(4819, 15), 4, 1, (13357, 13358), NAILS), // Bronze nails
  ??? -> SmithItem(new Item(819, 10), 4, 1, (1124, 1107), DART_TIPS), // Bronze dart tips
  ??? -> SmithItem(new Item(1794), 4, 1, (1132, 1096), WIRE), // Bronze wire
  ??? -> SmithItem(new Item(39, 15), 5, 1, (1130, 1108), ARROWTIPS), // Bronze arrowtips
  ??? -> SmithItem(new Item(1321), 5, 2, (1116, 1087), SCIMITAR), // Bronze scimitar
  ??? -> SmithItem(new Item(1291), 6, 2, (1089, 1086), LONGSWORD), // Bronze longsword
  ??? -> SmithItem(new Item(864, 5), 7, 1, (1131, 1106), KNIVES), // Bronze knives
  ??? -> SmithItem(new Item(1205), 7, 2, (1113, 1103), FULL_HELM), // Bronze full helm
  ??? -> SmithItem(new Item(1173), 8, 2, (1114, 1104), SQ_SHIELD), // Bronze sq. shield
  ??? -> SmithItem(new Item(1337), 9, 3, (1118, 1083), WARHAMMER), // Bronze warhammer
  ??? -> SmithItem(new Item(1375), 10, 3, (1095, 1092), BATTLE_AXE), // Bronze battleaxe
  ??? -> SmithItem(new Item(1103), 11, 3, (1109, 1098), CHAINBODY), // Bronze chainbody
  ??? -> SmithItem(new Item(1189), 12, 3, (1115, 1105), KITESHIELD), // Bronze kiteshield
  ??? -> SmithItem(new Item(1205), 13, 2, (8428, 8429), CLAWS), // Bronze claws
  ??? -> SmithItem(new Item(1205), 14, 3, (1090, 1088), TWO_HANDED_SWORD), // Bronze 2h sword
  ??? -> SmithItem(new Item(1075), 16, 3, (1110, 1099), PLATELEGS), // Bronze platelegs
  ??? -> SmithItem(new Item(1087), 16, 3, (1111, 1100), PLATESKIRT), // Bronze plateskirt
  ??? -> SmithItem(new Item(1117), 18, 5, (1112, 1101), PLATEBODY) // Bronze platebody
)

private val ADAMANT_BAR = 2349
private val ADAMANT_EXP = 12.5
private val ADAMANT_TABLE = Map(
  ??? -> SmithItem(new Item(1205), 1, 1, (1125, 1094), DAGGER), // Bronze dagger
  ??? -> SmithItem(new Item(1351), 1, 1, (1126, 1091), AXE), // Bronze axe
  ??? -> SmithItem(new Item(1422), 2, 1, (1129, 1093), MACE), // Bronze mace
  ??? -> SmithItem(new Item(1139), 3, 1, (1127, 1102), MED_HELM), // Bronze med helm
  ??? -> SmithItem(new Item(1277), 3, 1, (1128, 1085), SWORD), // Bronze sword
  ??? -> SmithItem(new Item(4819, 15), 4, 1, (13357, 13358), NAILS), // Bronze nails
  ??? -> SmithItem(new Item(819, 10), 4, 1, (1124, 1107), DART_TIPS), // Bronze dart tips
  ??? -> SmithItem(new Item(1794), 4, 1, (1132, 1096), WIRE), // Bronze wire
  ??? -> SmithItem(new Item(39, 15), 5, 1, (1130, 1108), ARROWTIPS), // Bronze arrowtips
  ??? -> SmithItem(new Item(1321), 5, 2, (1116, 1087), SCIMITAR), // Bronze scimitar
  ??? -> SmithItem(new Item(1291), 6, 2, (1089, 1086), LONGSWORD), // Bronze longsword
  ??? -> SmithItem(new Item(864, 5), 7, 1, (1131, 1106), KNIVES), // Bronze knives
  ??? -> SmithItem(new Item(1205), 7, 2, (1113, 1103), FULL_HELM), // Bronze full helm
  ??? -> SmithItem(new Item(1173), 8, 2, (1114, 1104), SQ_SHIELD), // Bronze sq. shield
  ??? -> SmithItem(new Item(1337), 9, 3, (1118, 1083), WARHAMMER), // Bronze warhammer
  ??? -> SmithItem(new Item(1375), 10, 3, (1095, 1092), BATTLE_AXE), // Bronze battleaxe
  ??? -> SmithItem(new Item(1103), 11, 3, (1109, 1098), CHAINBODY), // Bronze chainbody
  ??? -> SmithItem(new Item(1189), 12, 3, (1115, 1105), KITESHIELD), // Bronze kiteshield
  ??? -> SmithItem(new Item(1205), 13, 2, (8428, 8429), CLAWS), // Bronze claws
  ??? -> SmithItem(new Item(1205), 14, 3, (1090, 1088), TWO_HANDED_SWORD), // Bronze 2h sword
  ??? -> SmithItem(new Item(1075), 16, 3, (1110, 1099), PLATELEGS), // Bronze platelegs
  ??? -> SmithItem(new Item(1087), 16, 3, (1111, 1100), PLATESKIRT), // Bronze plateskirt
  ??? -> SmithItem(new Item(1117), 18, 5, (1112, 1101), PLATEBODY) // Bronze platebody
)

private val RUNE_BAR = 2349
private val RUNE_EXP = 12.5
private val RUNE_TABLE = Map(
  ??? -> SmithItem(new Item(1205), 1, 1, (1125, 1094), DAGGER), // Bronze dagger
  ??? -> SmithItem(new Item(1351), 1, 1, (1126, 1091), AXE), // Bronze axe
  ??? -> SmithItem(new Item(1422), 2, 1, (1129, 1093), MACE), // Bronze mace
  ??? -> SmithItem(new Item(1139), 3, 1, (1127, 1102), MED_HELM), // Bronze med helm
  ??? -> SmithItem(new Item(1277), 3, 1, (1128, 1085), SWORD), // Bronze sword
  ??? -> SmithItem(new Item(4819, 15), 4, 1, (13357, 13358), NAILS), // Bronze nails
  ??? -> SmithItem(new Item(819, 10), 4, 1, (1124, 1107), DART_TIPS), // Bronze dart tips
  ??? -> SmithItem(new Item(1794), 4, 1, (1132, 1096), WIRE), // Bronze wire
  ??? -> SmithItem(new Item(39, 15), 5, 1, (1130, 1108), ARROWTIPS), // Bronze arrowtips
  ??? -> SmithItem(new Item(1321), 5, 2, (1116, 1087), SCIMITAR), // Bronze scimitar
  ??? -> SmithItem(new Item(1291), 6, 2, (1089, 1086), LONGSWORD), // Bronze longsword
  ??? -> SmithItem(new Item(864, 5), 7, 1, (1131, 1106), KNIVES), // Bronze knives
  ??? -> SmithItem(new Item(1205), 7, 2, (1113, 1103), FULL_HELM), // Bronze full helm
  ??? -> SmithItem(new Item(1173), 8, 2, (1114, 1104), SQ_SHIELD), // Bronze sq. shield
  ??? -> SmithItem(new Item(1337), 9, 3, (1118, 1083), WARHAMMER), // Bronze warhammer
  ??? -> SmithItem(new Item(1375), 10, 3, (1095, 1092), BATTLE_AXE), // Bronze battleaxe
  ??? -> SmithItem(new Item(1103), 11, 3, (1109, 1098), CHAINBODY), // Bronze chainbody
  ??? -> SmithItem(new Item(1189), 12, 3, (1115, 1105), KITESHIELD), // Bronze kiteshield
  ??? -> SmithItem(new Item(1205), 13, 2, (8428, 8429), CLAWS), // Bronze claws
  ??? -> SmithItem(new Item(1205), 14, 3, (1090, 1088), TWO_HANDED_SWORD), // Bronze 2h sword
  ??? -> SmithItem(new Item(1075), 16, 3, (1110, 1099), PLATELEGS), // Bronze platelegs
  ??? -> SmithItem(new Item(1087), 16, 3, (1111, 1100), PLATESKIRT), // Bronze plateskirt
  ??? -> SmithItem(new Item(1117), 18, 5, (1112, 1101), PLATEBODY) // Bronze platebody
)
