package engine.bot.gear

import com.google.common.collect.ImmutableList
import io.luna.game.model.def.ItemDefinition

/**
 * Defines archetypal armor and gear sets that bots can reference.
 *
 * Each constant stores the minimum combat/style level used by bot selection, the item ids needed for the set,
 * and the high-level purposes that describe when bots should consider the set.
 *
 * These sets model one currently-equipped layout at a time. Ammunition, alternate switch weapons, and backup items
 * should be selected by their own systems rather than being stored in this layout enum.
 *
 * Every item id is documented inline with the cache item name when it exists in the supplied item dump. Items that
 * are not present in the supplied dump are explicitly marked as such instead of silently pretending they were found.
 *
 * @property level The minimum combat/style level used by bot gear selection logic.
 * @property equipment The item ids that make up this gear set.
 * @param firstPurpose The first purpose this gear set can satisfy.
 * @param additionalPurposes Any additional purposes this gear set can satisfy.
 *
 * @author lare96
 */
enum class BotGearSet(
    val level: Int,
    val equipment: Set<Int>,
    firstPurpose: BotGearPurpose,
    vararg additionalPurposes: BotGearPurpose
) : BotGearType {

    // Melee - regular metal armor.
    BRONZE_FULL_LEGS(
        1,
        setOf(
            1075, // Bronze platelegs
            1117, // Bronze platebody
            1155, // Bronze full helm
            1189, // Bronze kiteshield
            1321, // Bronze scimitar
            4119 // Bronze boots
        ),
        BotGearPurpose.MELEE
    ),

    BRONZE_FULL_SKIRT(
        1,
        setOf(
            1087, // Bronze plateskirt
            1117, // Bronze platebody
            1155, // Bronze full helm
            1189, // Bronze kiteshield
            1321, // Bronze scimitar
            4119 // Bronze boots
        ),
        BotGearPurpose.MELEE
    ),

    IRON_FULL_LEGS(
        1,
        setOf(
            1067, // Iron platelegs
            1115, // Iron platebody
            1153, // Iron full helm
            1191, // Iron kiteshield
            1323, // Iron scimitar
            4121 // Iron boots
        ),
        BotGearPurpose.MELEE
    ),

    IRON_FULL_SKIRT(
        1,
        setOf(
            1081, // Iron plateskirt
            1115, // Iron platebody
            1153, // Iron full helm
            1191, // Iron kiteshield
            1323, // Iron scimitar
            4121 // Iron boots
        ),
        BotGearPurpose.MELEE
    ),

    STEEL_FULL_LEGS(
        5,
        setOf(
            1069, // Steel platelegs
            1119, // Steel platebody
            1157, // Steel full helm
            1193, // Steel kiteshield
            1325, // Steel scimitar
            4123 // Steel boots
        ),
        BotGearPurpose.MELEE
    ),

    STEEL_FULL_SKIRT(
        5,
        setOf(
            1083, // Steel plateskirt
            1119, // Steel platebody
            1157, // Steel full helm
            1193, // Steel kiteshield
            1325, // Steel scimitar
            4123 // Steel boots
        ),
        BotGearPurpose.MELEE
    ),

    BLACK_FULL_LEGS(
        10,
        setOf(
            1077, // Black platelegs
            1125, // Black platebody
            1165, // Black full helm
            1195, // Black kiteshield
            1327, // Black scimitar
            4125 // Black boots
        ),
        BotGearPurpose.MELEE
    ),

    BLACK_FULL_SKIRT(
        10,
        setOf(
            1089, // Black plateskirt
            1125, // Black platebody
            1165, // Black full helm
            1195, // Black kiteshield
            1327, // Black scimitar
            4125 // Black boots
        ),
        BotGearPurpose.MELEE
    ),

    MITHRIL_FULL_LEGS(
        20,
        setOf(
            1071, // Mithril platelegs
            1121, // Mithril platebody
            1159, // Mithril full helm
            1197, // Mithril kiteshield
            1329, // Mithril scimitar
            4127 // Mithril boots
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    MITHRIL_FULL_SKIRT(
        20,
        setOf(
            1085, // Mithril plateskirt
            1121, // Mithril platebody
            1159, // Mithril full helm
            1197, // Mithril kiteshield
            1329, // Mithril scimitar
            4127 // Mithril boots
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    ADAMANT_FULL_LEGS(
        30,
        setOf(
            1073, // Adamant platelegs
            1123, // Adamant platebody
            1161, // Adamant full helm
            1199, // Adamant kiteshield
            1331, // Adamant scimitar
            4129 // Adamant boots
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    ADAMANT_FULL_SKIRT(
        30,
        setOf(
            1091, // Adamant plateskirt
            1123, // Adamant platebody
            1161, // Adamant full helm
            1199, // Adamant kiteshield
            1331, // Adamant scimitar
            4129 // Adamant boots
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    RUNE_FULL_LEGS(
        40,
        setOf(
            1079, // Rune platelegs
            1127, // Rune platebody
            1163, // Rune full helm
            1201, // Rune kiteshield
            1333, // Rune scimitar
            4131 // Rune boots
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    RUNE_FULL_SKIRT(
        40,
        setOf(
            1093, // Rune plateskirt
            1127, // Rune platebody
            1163, // Rune full helm
            1201, // Rune kiteshield
            1333, // Rune scimitar
            4131 // Rune boots
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    DRAGON_FULL_LEGS(
        60,
        setOf(
            4087, // Dragon platelegs
            3140, // Dragon chainbody
            1149, // Dragon med helm
            1187, // Dragon sq shield
            4587, // Dragon scimitar
            4131 // Rune boots
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    DRAGON_FULL_SKIRT(
        60,
        setOf(
            4585, // Dragon plateskirt
            3140, // Dragon chainbody
            1149, // Dragon med helm
            1187, // Dragon sq shield
            4587, // Dragon scimitar
            4131 // Rune boots
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    // Melee - Barrows.
    DHAROKS(
        70,
        setOf(
            4716, // Dharoks helm
            4718, // Dharoks greataxe
            4720, // Dharoks platebody
            4722 // Dharoks platelegs
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    VERACS(
        70,
        setOf(
            4753, // Veracs helm
            4755, // Veracs flail
            4757, // Veracs brassard
            4759 // Veracs plateskirt
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    TORAGS(
        70,
        setOf(
            4745, // Torags helm
            4747, // Torags hammers
            4749, // Torags platebody
            4751 // Torags platelegs
        ),
        BotGearPurpose.MELEE
    ),

    GUTHANS(
        70,
        setOf(
            4724, // Guthans helm
            4726, // Guthans warspear
            4728, // Guthans platebody
            4730 // Guthans chainskirt
        ),
        BotGearPurpose.MELEE
    ),

    // Ranged.
    LEATHER(
        1,
        setOf(
            1129, // Leather body
            1095, // Leather chaps
            1063, // Leather vambraces
            1167, // Leather cowl
            841 // Shortbow
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.RANGED
    ),

    STUDDED_LEATHER(
        20,
        setOf(
            1133, // Studded body
            1097, // Studded chaps
            1063, // Leather vambraces
            1169, // Coif
            849 // Willow shortbow
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.RANGED
    ),

    GREEN_DHIDE(
        40,
        setOf(
            1135, // Green d'hide body
            1099, // Green d'hide chaps
            1065, // Green d'hide vamb
            1165, // Black full helm
            861 // Magic shortbow
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.RANGED
    ),

    BLUE_DHIDE(
        50,
        setOf(
            2499, // Blue d'hide body
            2493, // Blue d'hide chaps
            2487, // Blue d'hide vamb
            2491, // Black d'hide vamb
            861 // Magic shortbow
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.RANGED
    ),

    RED_DHIDE(
        60,
        setOf(
            2501, // Red d'hide body
            2495, // Red d'hide chaps
            2489, // Red d'hide vamb
            2493, // Blue d'hide chaps
            861 // Magic shortbow
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.RANGED
    ),

    BLACK_DHIDE(
        70,
        setOf(
            2503, // Black d'hide body
            2497, // Black d'hide chaps
            2491, // Black d'hide vamb
            2493, // Blue d'hide chaps
            861 // Magic shortbow
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.RANGED
    ),

    KARILS(
        70,
        setOf(
            4732, // Karils coif
            4734, // Karils crossbow
            4736, // Karils leathertop
            4738 // Karils leatherskirt
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.RANGED
    ),

    // Magic.
    WIZARD_BLUE(
        1,
        setOf(
            579, // Wizard hat
            577, // Wizard robe
            1011, // Blue skirt
            1381 // Staff of air
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MAGIC
    ),

    WIZARD_DARK(
        10,
        setOf(
            4089, // Mystic hat
            4091, // Mystic robe top
            4093, // Mystic robe bottom
            1381 // Staff of air
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MAGIC
    ),

    MYSTIC_LIGHT(
        40,
        setOf(
            4089, // Mystic hat
            4091, // Mystic robe top
            4093, // Mystic robe bottom
            4095, // Mystic gloves
            4097, // Mystic boots
            4675 // Ancient staff
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MAGIC
    ),

    MYSTIC_DARK(
        40,
        setOf(
            4101, // Mystic robe top
            4103, // Mystic robe bottom
            4105, // Mystic gloves
            4107, // Mystic boots
            4109, // Mystic hat
            4675 // Ancient staff
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MAGIC
    ),

    AHRIMS(
        70,
        setOf(
            4708, // Ahrims hood
            4710, // Ahrims staff
            4712, // Ahrims robetop
            4714 // Ahrims robeskirt
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MAGIC
    ),

    // Show-off armor - trimmed and gold.
    BLACK_TRIMMED_LEGS(
        1,
        setOf(
            2587, // Black full helm (t)
            2583, // Black platebody (t)
            2585, // Black platelegs (t)
            2589, // Black kiteshield (t)
            4125 // Black boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    BLACK_TRIMMED_SKIRT(
        1,
        setOf(
            2587, // Black full helm (t)
            2583, // Black platebody (t)
            3472, // Black plateskirt (t)
            2589, // Black kiteshield (t)
            4125 // Black boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    BLACK_GOLD_LEGS(
        1,
        setOf(
            2595, // Black full helm (g)
            2591, // Black platebody (g)
            2593, // Black platelegs (g)
            2597, // Black kiteshield (g)
            4125 // Black boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    BLACK_GOLD_SKIRT(
        1,
        setOf(
            2595, // Black full helm (g)
            2591, // Black platebody (g)
            3473, // Black plateskirt (g)
            2597, // Black kiteshield (g)
            4125 // Black boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    ADAMANT_TRIMMED_LEGS(
        30,
        setOf(
            2605, // Adam full helm (t)
            2599, // Adam platebody (t)
            2601, // Adam platelegs (t)
            2603, // Adam kiteshield (t)
            4129 // Adamant boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    ADAMANT_TRIMMED_SKIRT(
        30,
        setOf(
            2605, // Adam full helm (t)
            2599, // Adam platebody (t)
            3474, // Adam plateskirt (t)
            2603, // Adam kiteshield (t)
            4129 // Adamant boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    ADAMANT_GOLD_LEGS(
        30,
        setOf(
            2613, // Adam full helm (g)
            2607, // Adam platebody (g)
            2609, // Adam platelegs (g)
            2611, // Adam kiteshield (g)
            4129 // Adamant boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    ADAMANT_GOLD_SKIRT(
        30,
        setOf(
            2613, // Adam full helm (g)
            2607, // Adam platebody (g)
            3475, // Adam plateskirt (g)
            2611, // Adam kiteshield (g)
            4129 // Adamant boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_TRIMMED_LEGS(
        40,
        setOf(
            2623, // Rune platebody (t)
            2625, // Rune platelegs (t)
            2627, // Rune full helm (t)
            2629, // Rune kiteshield (t)
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_TRIMMED_SKIRT(
        40,
        setOf(
            2623, // Rune platebody (t)
            3477, // Rune plateskirt (t)
            2627, // Rune full helm (t)
            2629, // Rune kiteshield (t)
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_GOLD_LEGS(
        40,
        setOf(
            2615, // Rune platebody (g)
            2617, // Rune platelegs (g)
            2619, // Rune full helm (g)
            2621, // Rune kiteshield (g)
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_GOLD_SKIRT(
        40,
        setOf(
            2615, // Rune platebody (g)
            3476, // Rune plateskirt (g)
            2619, // Rune full helm (g)
            2621, // Rune kiteshield (g)
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    // Show-off armor - god rune.
    RUNE_SARADOMIN_LEGS(
        40,
        setOf(
            2661, // Saradomin plate
            2663, // Saradomin legs
            2665, // Saradomin full
            2667, // Saradomin kite
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_SARADOMIN_SKIRT(
        40,
        setOf(
            2661, // Saradomin plate
            3479, // Saradomin skirt
            2665, // Saradomin full
            2667, // Saradomin kite
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_GUTHIX_LEGS(
        40,
        setOf(
            2673, // Guthix full helm
            2669, // Guthix platebody
            2671, // Guthix platelegs
            2675, // Guthix kiteshield
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_GUTHIX_SKIRT(
        40,
        setOf(
            2673, // Guthix full helm
            2669, // Guthix platebody
            3480, // Guthix plateskirt
            2675, // Guthix kiteshield
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_ZAMORAK_LEGS(
        40,
        setOf(
            2653, // Zamorak platebody
            2655, // Zamorak platelegs
            2657, // Zamorak full helm
            2659, // Zamorak kiteshield
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    RUNE_ZAMORAK_SKIRT(
        40,
        setOf(
            2653, // Zamorak platebody
            3478, // Zamorak plateskirt
            2657, // Zamorak full helm
            2659, // Zamorak kiteshield
            4131 // Rune boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    // Show-off armor - random event / cosmetic sets.
    ZAMORAK_ROBES(
        1,
        setOf(
            1033, // Zamorak robe
            1035, // Zamorak robe
            2414, // Zamorak cape
            2417 // Zamorak staff
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.MAGIC
    ),

    MIME(
        1,
        setOf(
            3057, // Mime mask
            3058, // Mime top
            3059, // Mime legs
            3060, // Mime gloves
            3061 // Mime boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    LEDERHOSEN(
        1,
        setOf(
            6182, // Lederhosen hat
            6180, // Lederhosen top
            6181 // Lederhosen shorts
        ),
        BotGearPurpose.SHOW_OFF
    ),

    FROG_PRINCE(
        1,
        setOf(
            6188, // Frog mask
            6184, // Prince tunic
            6185 // Prince leggings
        ),
        BotGearPurpose.SHOW_OFF
    ),

    FROG_PRINCESS(
        1,
        setOf(
            6188, // Frog mask
            6186, // Princess blouse
            6187 // Princess skirt
        ),
        BotGearPurpose.SHOW_OFF
    ),

    PIRATE_A(
        1,
        setOf(
            7112, // Pirate bandanna
            7110, // Stripy pirate shirt
            7116 // Pirate leggings
        ),
        BotGearPurpose.SHOW_OFF
    ),

    PIRATE_B(
        1,
        setOf(
            7124, // Pirate bandanna
            7122, // Stripy pirate shirt
            7126 // Pirate leggings
        ),
        BotGearPurpose.SHOW_OFF
    ),

    PIRATE_C(
        1,
        setOf(
            7130, // Pirate bandanna
            7128, // Stripy pirate shirt
            7132 // Pirate leggings
        ),
        BotGearPurpose.SHOW_OFF
    ),

    PIRATE_D(
        1,
        setOf(
            7136, // Pirate bandanna
            7134, // Stripy pirate shirt
            7138 // Pirate leggings
        ),
        BotGearPurpose.SHOW_OFF
    ),

    // Show-off armor - gilded rune.
    GILDED_RUNE_LEGS(
        40,
        setOf(
            3486, // Gilded full helm
            3481, // Gilded platebody
            3483, // Gilded platelegs
            3488 // Gilded kiteshield
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.MELEE
    ),

    GILDED_RUNE_SKIRT(
        40,
        setOf(
            3486, // Gilded full helm
            3481, // Gilded platebody
            3485, // Gilded plateskirt
            3488 // Gilded kiteshield
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.MELEE
    ),

    // Hybrid training outfits - ranged/melee.
    BRONZE_LEATHER_HYBRID(
        1,
        setOf(
            1075, // Bronze platelegs
            1129, // Leather body
            1095, // Leather chaps
            1063, // Leather vambraces
            1167, // Leather cowl
            1061, // Leather boots
            841 // Shortbow
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    IRON_LEATHER_HYBRID(
        1,
        setOf(
            1067, // Iron platelegs
            1129, // Leather body
            1095, // Leather chaps
            1063, // Leather vambraces
            1169, // Coif
            1061, // Leather boots
            843 // Oak shortbow
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    STEEL_STUDDED_HYBRID(
        5,
        setOf(
            1069, // Steel platelegs
            1133, // Studded body
            1097, // Studded chaps
            1063, // Leather vambraces
            1169, // Coif
            4123, // Steel boots
            849 // Willow shortbow
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    BLACK_STUDDED_HYBRID(
        10,
        setOf(
            1077, // Black platelegs
            1133, // Studded body
            1097, // Studded chaps
            1063, // Leather vambraces
            1169, // Coif
            4125, // Black boots
            849 // Willow shortbow
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    MITHRIL_GREEN_DHIDE_HYBRID(
        20,
        setOf(
            1071, // Mithril platelegs
            1135, // Green d'hide body
            1099, // Green d'hide chaps
            1065, // Green d'hide vamb
            1169, // Coif
            4127, // Mithril boots
            853 // Maple shortbow
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    ADAMANT_BLUE_DHIDE_HYBRID(
        30,
        setOf(
            1073, // Adamant platelegs
            2499, // Blue d'hide body
            2493, // Blue d'hide chaps
            2487, // Blue d'hide vamb
            1169, // Coif
            4129, // Adamant boots
            857 // Yew shortbow
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    RUNE_BLACK_DHIDE_HYBRID(
        40,
        setOf(
            1079, // Rune platelegs
            2503, // Black d'hide body
            2497, // Black d'hide chaps
            2491, // Black d'hide vamb
            1169, // Coif
            4131, // Rune boots
            861 // Magic shortbow
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    DRAGON_BLACK_DHIDE_HYBRID(
        60,
        setOf(
            4087, // Dragon platelegs
            2503, // Black d'hide body
            2497, // Black d'hide chaps
            2491, // Black d'hide vamb
            1169, // Coif
            4131, // Rune boots
            861 // Magic shortbow
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    // Hybrid training outfits - magic/melee.
    BRONZE_WIZARD_HYBRID(
        1,
        setOf(
            1075, // Bronze platelegs
            579, // Wizard hat
            577, // Wizard robe
            2579, // Wizard boots
            1381 // Staff of air
        ),
        BotGearPurpose.MAGIC
    ),

    IRON_WIZARD_HYBRID(
        1,
        setOf(
            1067, // Iron platelegs
            579, // Wizard hat
            577, // Wizard robe
            2579, // Wizard boots
            1381 // Staff of air
        ),
        BotGearPurpose.MAGIC
    ),

    STEEL_WIZARD_HYBRID(
        5,
        setOf(
            1069, // Steel platelegs
            579, // Wizard hat
            577, // Wizard robe
            2579, // Wizard boots
            1381 // Staff of air
        ),
        BotGearPurpose.MAGIC
    ),

    BLACK_WIZARD_HYBRID(
        10,
        setOf(
            1077, // Black platelegs
            579, // Wizard hat
            577, // Wizard robe
            2579, // Wizard boots
            1381 // Staff of air
        ),
        BotGearPurpose.MAGIC
    ),

    MITHRIL_WIZARD_HYBRID(
        20,
        setOf(
            1071, // Mithril platelegs
            579, // Wizard hat
            577, // Wizard robe
            2579, // Wizard boots
            1381 // Staff of air
        ),
        BotGearPurpose.MAGIC
    ),

    ADAMANT_WIZARD_HYBRID(
        30,
        setOf(
            1073, // Adamant platelegs
            579, // Wizard hat
            577, // Wizard robe
            2579, // Wizard boots
            4675 // Ancient staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    RUNE_WIZARD_HYBRID(
        40,
        setOf(
            1079, // Rune platelegs
            579, // Wizard hat
            577, // Wizard robe
            2579, // Wizard boots
            4675 // Ancient staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    DRAGON_WIZARD_HYBRID(
        60,
        setOf(
            4087, // Dragon platelegs
            579, // Wizard hat
            577, // Wizard robe
            2579, // Wizard boots
            4675 // Ancient staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    // Specific PKing outfits.
    F2P_RUNE_2H_PURE(
        40,
        setOf(
            1135, // Green d'hide body
            1099, // Green d'hide chaps
            1065, // Green d'hide vamb
            1169, // Coif
            1725, // Amulet of strength
            1319 // Rune 2h sword
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    F2P_RUNE_SCIMITAR_2H(
        40,
        setOf(
            1135, // Green d'hide body
            1099, // Green d'hide chaps
            1065, // Green d'hide vamb
            1169, // Coif
            1725, // Amulet of strength
            1333, // Rune scimitar
            1319 // Rune 2h sword
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    RUNE_DHIDE_SWITCHER(
        40,
        setOf(
            1079, // Rune platelegs
            2503, // Black d'hide body
            2497, // Black d'hide chaps
            2491, // Black d'hide vamb
            4131, // Rune boots
            1704, // Amulet of glory
            861, // Magic shortbow
            1333, // Rune scimitar
            1215 // Dragon dagger
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    BLACK_DHIDE_RUNE_LEGS_TRIBRID(
        40,
        setOf(
            1079, // Rune platelegs
            2503, // Black d'hide body
            2497, // Black d'hide chaps
            2491, // Black d'hide vamb
            579, // Wizard hat
            2579, // Wizard boots
            4675, // Ancient staff
            861, // Magic shortbow
            1215 // Dragon dagger
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC
    ),

    BLACK_DHIDE_DRAGON_LEGS_TRIBRID(
        60,
        setOf(
            4087, // Dragon platelegs
            2503, // Black d'hide body
            2497, // Black d'hide chaps
            2491, // Black d'hide vamb
            579, // Wizard hat
            2579, // Wizard boots
            4675, // Ancient staff
            861, // Magic shortbow
            1215, // Dragon dagger
            4587 // Dragon scimitar
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC
    ),

    OBSIDIAN_MELEE(
        60,
        setOf(
            3751, // Berserker helm
            6568, // Obsidian cape
            6524, // Toktz-ket-xil
            6527, // Tzhaar-ket-em
            6528, // Tzhaar-ket-om
            6737 // Berserker ring
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.SHOW_OFF
    ),

    OBSIDIAN_RANGE_MELEE_HYBRID(
        60,
        setOf(
            3751, // Berserker helm
            6568, // Obsidian cape
            6524, // Toktz-ket-xil
            6522, // Toktz-xil-ul
            6523, // Toktz-xil-ak
            6527, // Tzhaar-ket-em
            6737 // Berserker ring
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.SHOW_OFF
    ),

    // Fremennik armor sets.
    ROCKSHELL(
        50,
        setOf(
            6128, // Rock-shell helm
            6129, // Rock-shell plate
            6130, // Rock-shell legs
            6151, // Rock-shell gloves
            6145 // Rock-shell boots
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    SPINED(
        40,
        setOf(
            6131, // Spined helm
            6133, // Spined body
            6135, // Spined chaps
            6149, // Spined gloves
            6143 // Spined boots
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    SKELETAL(
        40,
        setOf(
            6137, // Skeletal helm
            6139, // Skeletal top
            6141, // Skeletal bottoms
            6153, // Skeletal gloves
            6147 // Skeletal boots
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    // Magic and cosmetic robe sets.
    SPLITBARK(
        40,
        setOf(
            3385, // Splitbark helm
            3387, // Splitbark body
            3389, // Splitbark legs
            3391, // Splitbark gauntlets
            3393 // Splitbark greaves
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    INFINITY(
        50,
        setOf(
            6918, // Infinity hat
            6916, // Infinity top
            6924, // Infinity bottoms
            6922, // Infinity gloves
            6920 // Infinity boots
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    GHOSTLY(
        1,
        setOf(
            6109, // Ghostly hood
            6107, // Ghostly robe
            6108, // Ghostly robe
            6110, // Ghostly gloves
            6106, // Ghostly boots
            6111 // Ghostly cloak
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    // Random event, quest, and disguise sets.
    HAM(
        1,
        setOf(
            4298, // Ham shirt
            4300, // Ham robe
            4302, // Ham hood
            4304 // Ham cloak
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    MOURNER(
        1,
        setOf(
            6065, // Mourner top
            6067, // Mourner trousers
            6068, // Mourner gloves
            6069, // Mourner boots
            6070 // Mourner cloak
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    ROGUE(
        1,
        setOf(
            5553, // Rogue top
            5554, // Rogue mask
            5555, // Rogue trousers
            5556, // Rogue gloves
            5557 // Rogue boots
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    );

    companion object {
        val VALUES = ImmutableList.copyOf(values())
    }
    /**
     * The high-level reasons a bot may choose this gear set.
     */
    val purposes: Set<BotGearPurpose> = setOf(firstPurpose, *additionalPurposes)

    override fun priority(): Int {
        return level
    }

    override fun ids(): Set<Int> {
        return equipment
    }

    override fun purposes(): Set<BotGearPurpose> {
        return purposes
    }

    /**
     * Returns the combined item weight for this gear set.
     *
     * Item ids missing from the local cache weight table count as `0.0`. This keeps post-377 or custom ids safe
     * until their cache definitions are available.
     *
     * @return The total known weight of every item in this gear set.
     */
    val totalWeight: Double
        get() {
            var total = 0.0
            for (id in equipment) {
                total += ItemDefinition.ALL.get(id).map(ItemDefinition::getWeight).orElse(0.0)
            }
            return total
        }
}
