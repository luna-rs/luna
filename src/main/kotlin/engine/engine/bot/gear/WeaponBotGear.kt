package engine.bot.gear

/**
 * Defines weapon-slot gear groups that bots can use when selecting equipment.
 *
 * These groups are intentionally broad. Metal weapon groups include common melee weapons and their poisoned dagger
 * or spear variants where they exist. Ranged groups include bows, crossbows, thrown weapons, and special ranged
 * weapons. Magic groups include regular staffs, elemental staffs, battlestaffs, wands, god staffs, and specialty
 * magic weapons.
 *
 * Some weapons still need script-specific handling. For example, elemental staff selection should usually be done by
 * the magic/combat script when it knows which spell family it intends to cast.
 *
 * @param priority The relative priority of this weapon group.
 * @param ids The item ids that belong to this weapon group.
 * @param firstPurpose The first equipment purpose this weapon group satisfies.
 * @param additionalPurposes Any additional equipment purposes this weapon group satisfies.
 *
 * @author lare96
 */
enum class WeaponBotGear(private val priority: Int,
                         val ids: Set<Int>,
                         firstPurpose: BotGearPurpose,
                         vararg additionalPurposes: BotGearPurpose) : BotGearType {

    BRONZE_WEAPONS(
        0,
        setOf(
            1205, 1221, 5670, 5688, // Bronze daggers
            1237, 1251, 3170, 5704, 5718, // Bronze spears
            1277, 1291, 1307, 1321, // Bronze swords
            1337, 1375, 1422, 3190 // Bronze heavy weapons
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    IRON_WEAPONS(
        1,
        setOf(
            1203, 1219, 5668, 5686, // Iron daggers
            1239, 1253, 3171, 5706, 5720, // Iron spears
            1279, 1293, 1309, 1323, // Iron swords
            1335, 1363, 1420, 3192 // Iron heavy weapons
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    STEEL_WEAPONS(
        2,
        setOf(
            1207, 1223, 5672, 5690, // Steel daggers
            1241, 1255, 3172, 5708, 5722, // Steel spears
            1281, 1295, 1311, 1325, // Steel swords
            1339, 1365, 1424, 3194 // Steel heavy weapons
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    BLACK_WEAPONS(
        3,
        setOf(
            1217, 1233, 5682, 5700, // Black daggers
            4580, 4582, 4584, 5734, 5736, // Black spears
            1283, 1297, 1313, 1327, // Black swords
            1341, 1367, 1426, 3196 // Black heavy weapons
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    MITHRIL_WEAPONS(
        4,
        setOf(
            1209, 1225, 5674, 5692, // Mithril daggers
            1243, 1257, 3173, 5710, 5724, // Mithril spears
            1285, 1299, 1315, 1329, // Mithril swords
            1343, 1369, 1428, 3198 // Mithril heavy weapons
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    ADAMANT_WEAPONS(
        5,
        setOf(
            1211, 1227, 5676, 5694, // Adamant daggers
            1245, 1259, 3174, 5712, 5726, // Adamant spears
            1287, 1301, 1317, 1331, // Adamant swords
            1345, 1371, 1430, 3200 // Adamant heavy weapons
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    RUNE_WEAPONS(
        6,
        setOf(
            1213, 1229, 5678, 5696, // Rune daggers
            1247, 1261, 3175, 5714, 5728, // Rune spears
            1289, 1303, 6897, 1319, 1333, // Rune swords
            1347, 1373, 1432, 3202 // Rune heavy weapons
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    DRAGON_WEAPONS(
        7,
        setOf(
            1215, 1231, 5680, 5698, // Dragon daggers
            1249, 1263, 3176, 5716, 5730, // Dragon spears
            1305, 4587, 7158, // Dragon swords
            1377, 1434, 3204 // Dragon heavy weapons
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    WHITE_WEAPONS(
        4,
        setOf(
            6589, // White battleaxe
            6591, 6593, 6595, 6597, // White daggers
            6599, // White halberd
            6601, // White mace
            6605, 6607, 6609, 6611, // White swords
            6613 // White warhammer
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.SHOW_OFF
    ),

    TZHAAR_MELEE_WEAPONS(
        8,
        setOf(
            6523, // Toktz-xil-ak
            6525, // Toktz-xil-ek
            6527, // Tzhaar-ket-em
            6528 // Tzhaar-ket-om
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    GRANITE_MAUL(
        9,
        setOf(
            4153 // Granite maul
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    BARROWS_MELEE_WEAPONS(
        9,
        setOf(
            4718, 4886, 4887, 4888, 4889, // Dharok's greataxe
            4726, 4910, 4911, 4912, 4913, // Guthan's warspear
            4747, 4958, 4959, 4960, 4961, // Torag's hammers
            4755, 4982, 4983, 4984, 4985 // Verac's flail
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    ABYSSAL_WHIP(
        10,
        setOf(
            4151 // Abyssal whip
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    QUEST_MELEE_WEAPONS(
        4,
        setOf(
            35, // Excalibur
            667, // Blurite sword
            746, // Dark dagger
            747, // Glowing dagger
            2402, 6745, // Silverlight
            2952, // Wolfbane
            4158, // Leaf-bladed spear
            6746, // Darklight
            7668, // Gadderhammer
            7806, // Anger sword
            7807, // Anger battleaxe
            7808, // Anger mace
            7809 // Anger spear
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.SHOW_OFF
    ),

    DECORATIVE_MELEE_WEAPONS(
        3,
        setOf(
            3757, // Fremennik blade
            4068, 4503, 4508, // Decorative swords
            5016, // Bone spear
            5018, // Bone club
            6313, // Opal machete
            6315, // Jade machete
            6317, // Red topaz machete
            7140, // Lucky cutlass
            7141, // Harry's cutlass
            7142 // Rapier
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.SHOW_OFF
    ),

    MJOLNIRS(
        5,
        setOf(
            6760, // Guthix mjolnir
            6762, // Saradomin mjolnir
            6764, // Zamorak mjolnir
            7804 // Zaros mjolnir
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.SHOW_OFF
    ),

    FUN_WEAPONS(
        0,
        setOf(
            1419, // Scythe
            2460, 2462, 2464, 2466, 2468, 2470, 2472, 2474, 2476, // Flowers
            4565, // Basket of eggs
            4566, // Rubber chicken
            6541 // Mouse toy
        ),
        BotGearPurpose.SHOW_OFF
    ),

    CROSSBOWS(
        0,
        setOf(
            767, // Phoenix crossbow
            837 // Crossbow
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    BASIC_BOWS(
        0,
        setOf(
            839, // Longbow
            841 // Shortbow
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    OAK_BOWS(
        1,
        setOf(
            843, // Oak shortbow
            845, // Oak longbow
            4236 // Oak longbow
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    WILLOW_BOWS(
        2,
        setOf(
            847, // Willow longbow
            849 // Willow shortbow
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    MAPLE_BOWS(
        3,
        setOf(
            851, // Maple longbow
            853 // Maple shortbow
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    YEW_BOWS(
        4,
        setOf(
            855, // Yew longbow
            857 // Yew shortbow
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    MAGIC_BOWS(
        5,
        setOf(
            859, // Magic longbow
            861 // Magic shortbow
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    OGRE_BOWS(
        3,
        setOf(
            2883, // Ogre bow
            4827 // Comp ogre bow
        ),
        BotGearPurpose.RANGED
    ),

    CRYSTAL_BOW(
        8,
        setOf(
            4212, // New crystal bow
            4214, // Crystal bow full
            4215, // Crystal bow 9/10
            4216, // Crystal bow 8/10
            4217, // Crystal bow 7/10
            4218, // Crystal bow 6/10
            4219, // Crystal bow 5/10
            4220, // Crystal bow 4/10
            4221, // Crystal bow 3/10
            4222, // Crystal bow 2/10
            4223 // Crystal bow 1/10
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    KARILS_CROSSBOW(
        8,
        setOf(
            4734, // Karil's crossbow
            4934, // Karil's x-bow 100
            4935, // Karil's x-bow 75
            4936, // Karil's x-bow 50
            4937 // Karil's x-bow 25
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    SEERCULL(
        7,
        setOf(
            6724 // Seercull
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    TOKTZ_THROWN_WEAPONS(
        4,
        setOf(
            6522 // Toktz-xil-ul
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    BRONZE_THROWN_WEAPONS(
        0,
        setOf(
            800, // Bronze thrownaxe
            806, 812, 5628, 5635, // Bronze darts
            825, 831, 5642, 5648, // Bronze javelins
            864, 870, 5654, 5661 // Bronze knives
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    IRON_THROWN_WEAPONS(
        1,
        setOf(
            801, // Iron thrownaxe
            807, 813, 5629, 5636, // Iron darts
            826, 832, 5643, 5649, // Iron javelins
            863, 871, 5655, 5662 // Iron knives
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    STEEL_THROWN_WEAPONS(
        2,
        setOf(
            802, // Steel thrownaxe
            808, 814, 5630, 5637, // Steel darts
            827, 833, 5644, 5650, // Steel javelins
            865, 872, 5656, 5663 // Steel knives
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    BLACK_THROWN_WEAPONS(
        3,
        setOf(
            3093, 3094, 5631, 5638, // Black darts
            869, 874, 5658, 5665 // Black knives
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    MITHRIL_THROWN_WEAPONS(
        4,
        setOf(
            803, // Mithril thrownaxe
            809, 815, 5632, 5639, // Mithril darts
            828, 834, 5645, 5651, // Mithril javelins
            866, 873, 5657, 5664 // Mithril knives
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    ADAMANT_THROWN_WEAPONS(
        5,
        setOf(
            804, // Adamant thrownaxe
            810, 816, 5633, 5640, // Adamant darts
            829, 835, 5646, 5652, // Adamant javelins
            867, 875, 5659, 5666 // Adamant knives
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    RUNE_THROWN_WEAPONS(
        6,
        setOf(
            805, // Rune thrownaxe
            811, 817, 5634, 5641, // Rune darts
            830, 836, 5647, 5653, // Rune javelins
            868, 876, 5660, 5667 // Rune knives
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    POISONED_DART(
        0,
        setOf(
            818 // Poisoned dart(p)
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    STAFF(
        0,
        setOf(
            1379 // Staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    MAGIC_STAFF(
        1,
        setOf(
            1389, // Magic staff
            6603 // Magic staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.SHOW_OFF
    ),

    ELEMENTAL_STAFF(
        2,
        setOf(
            1381, // Staff of air
            1383, // Staff of water
            1385, // Staff of earth
            1387 // Staff of fire
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    BATTLESTAFF(
        3,
        setOf(
            1391 // Battlestaff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    ELEMENTAL_BATTLESTAFF(
        4,
        setOf(
            1393, // Fire battlestaff
            1395, // Water battlestaff
            1397, // Air battlestaff
            1399, // Earth battlestaff
            3053, // Lava battlestaff
            6562 // Mud battlestaff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    MYSTIC_STAFF(
        5,
        setOf(
            1401, // Mystic fire staff
            1403, // Mystic water staff
            1405, // Mystic air staff
            1407, // Mystic earth staff
            3054, // Mystic lava staff
            6563 // Mystic mud staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    ANCIENT_STAFF(
        6,
        setOf(
            4675 // Ancient staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    TOKTZ_MEJ_TAL(
        6,
        setOf(
            6526 // Toktz-mej-tal
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    SLAYERS_STAFF(
        6,
        setOf(
            4170 // Slayer's staff
        ),
        BotGearPurpose.MAGIC
    ),

    AHRIMS_STAFF(
        8,
        setOf(
            4710, // Ahrim's staff
            4862, // Ahrim's staff 100
            4863, // Ahrim's staff 75
            4864, // Ahrim's staff 50
            4865 // Ahrim's staff 25
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    BEGINNER_WAND(
        6,
        setOf(
            6908 // Beginner wand
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.SHOW_OFF
    ),

    APPRENTICE_WAND(
        7,
        setOf(
            6910 // Apprentice wand
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.SHOW_OFF
    ),

    TEACHER_WAND(
        8,
        setOf(
            6912 // Teacher wand
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.SHOW_OFF
    ),

    MASTER_WAND(
        9,
        setOf(
            6914 // Master wand
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    IBAN_STAFF(
        10,
        setOf(
            1409 // Iban's staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    GOD_STAVES(
        11,
        setOf(
            2415, // Saradomin staff
            2416, // Guthix staff
            2417 // Zamorak staff
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    );

    /**
     * The equipment purposes this weapon group can satisfy.
     */
    val purposeSet: Set<BotGearPurpose> = setOf(firstPurpose, *additionalPurposes)

    override fun priority(): Int {
        return priority
    }

    override fun containsId(id: Int): Boolean {
        return id in ids
    }

    override fun containsPurpose(purpose: BotGearPurpose): Boolean {
        return purpose in purposeSet
    }

    override fun ids(): Set<Int> {
        return ids
    }

    override fun purposes(): Set<BotGearPurpose> {
        return purposeSet
    }
}