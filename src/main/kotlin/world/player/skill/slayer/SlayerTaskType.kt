package world.player.skill.slayer

import api.predef.defence
import com.google.common.collect.ImmutableSet
import io.luna.game.model.mob.Player

/**
 * An enum representing a specific type of slayer task.
 */
enum class SlayerTaskType(
    val level: Int = 1,
    val plural: String,
    val npcs: Set<Int>,
    val difficulty: Player.() -> Boolean = { true },
    val tip: String = "Err... Well..? Have fun!"
) {
    MONKEY(
        plural = "monkeys",
        npcs = setOf(132, 1456, 1459, 1465, 1467, 1466)
    ),
    ROCK_CRAB(
        plural = "rock crabs",
        npcs = setOf(1265, 1267, 2452, 2885)
    ),
    SPIDER(
        plural = "spiders",
        npcs = setOf(58, 59, 60, 61, 62, 63, 64, 134, 1009, 1221, 1473, 1474, 2035, 2491, 2492, 2850, 3585, 997)
    ),
    BIRD(
        plural = "birds",
        npcs = setOf(
            41, 44, 45, 48, 131, 136, 951, 1015, 1017, 1018, 1401, 1402, 1403, 1475, 1476, 1692, 1996, 2313,
            2314, 2315, 2693, 2694, 3675, 3676
        )
    ),
    RAT(
        plural = "rats",
        npcs = setOf(47, 86, 87, 88, 224, 446, 748, 950, 2032, 2033, 2682, 2722, 2723, 2981)
    ),
    GOBLIN(
        plural = "goblins",
        npcs = setOf(
            100, 101, 102, 298, 299, 444, 445, 1769, 1770, 1771, 1772, 1773, 1774, 1775, 1776, 1822, 1823,
            1824, 1825, 2069, 2070, 2071, 2072, 2073, 2074, 2075, 2076, 2077, 2078, 2274, 2275, 2276, 2277,
            2278, 2279, 2280, 2281, 2678, 2679, 2680, 2681, 3264, 3265, 3266, 3267
        )
    ),
    BAT(plural = "bats",
        npcs = setOf(78, 79, 412),
        difficulty = { combatLevel >= 5 }),
    COW(plural = "cows",
        npcs = setOf(81, 397, 955, 1767, 3309),
        difficulty = { combatLevel >= 5 }),
    DWARF(plural = "dwarves",
        npcs = setOf(
            118, 119, 120, 121, 1795, 1796, 1797, 2130, 2131, 2132, 2133, 2134, 2135, 2136, 3219, 3220,
            3221, 3268, 3269, 3270, 3271, 3272, 3273, 3274, 3275, 3276, 3277, 3278, 3279
        ),
        difficulty = { combatLevel >= 6 }),
    SCORPION(plural = "scorpions",
        npcs = setOf(107, 108, 109, 144, 493, 1477, 1693),
        difficulty = { combatLevel >= 7 }),
    ZOMBIE(plural = "zombies",
        npcs = setOf(
            73, 74, 75, 76, 419, 420, 421, 422, 423, 424, 502, 503, 504, 505, 751, 1465, 1466, 1467, 1691,
            1692, 2044, 2045, 2046, 2047, 2048, 2049, 2051, 2052, 2053, 2054, 2055, 2058, 2837, 2838, 2839,
            2840, 2841, 2842, 2843, 2844, 2845, 2846, 2847, 2848, 3622
        ),
        difficulty = { combatLevel >= 10 }),
    BEAR(plural = "bears",
        npcs = setOf(1195, 1196, 1197, 1326, 1327),
        difficulty = { combatLevel >= 13 }),
    GHOST(plural = "ghosts",
        npcs = setOf(79, 103, 104, 491, 749, 1549, 1698, 1541, 2716, 2931),
        difficulty = { combatLevel >= 13 }),
    DOG(plural = "dogs",
        npcs = setOf(99, 1047, 1593, 1594, 1976, 1994, 3582),
        difficulty = { combatLevel >= 15 }),
    KALPHITE(plural = "kalphite",
        npcs = setOf(1153, 1154, 1155, 1156, 1157, 1158, 1160, 3589, 3835, 3836),
        difficulty = { combatLevel >= 15 }),
    SKELETON(plural = "skeletons",
        npcs = setOf(90, 91, 92, 93, 459, 750, 1471, 1515, 2036, 2037, 2050, 2056, 2057, 2724, 2725, 3291, 3581),
        difficulty = { combatLevel >= 15 }),
    ICEFIEND(plural = "icefiends",
        npcs = setOf(3406),
        difficulty = { combatLevel >= 20 }),
    WOLF(plural = "wolves",
        npcs = setOf(95, 141, 142, 143, 839, 1198, 1330, 1558, 1559, 1951, 1952, 1953, 1954, 1955, 1956),
        difficulty = { combatLevel >= 20 }),
    HOBGOBLIN(plural = "hobgoblins",
        npcs = setOf(122, 123, 2685, 2686, 2687, 2688, 3583),
        difficulty = { combatLevel >= 20 }),
    LIZARD(
        level = 22,
        plural = "lizards",
        npcs = setOf(2803, 2804, 2805, 2806, 2807, 2808)
    ),
    GHOUL(plural = "ghouls",
        npcs = setOf(1218, 3059),
        difficulty = { combatLevel >= 25 }),
    HILL_GIANT(plural = "hill giants",
        npcs = setOf(116, 117),
        difficulty = { combatLevel >= 25 }),
    SHADE(plural = "shades",
        npcs = setOf(425, 426, 427, 428, 429, 430, 1241, 1244, 1246, 1248, 1250, 3617),
        difficulty = { combatLevel >= 30 }),
    OTHERWORLDLY_BEING(plural = "otherworldly beings",
        npcs = setOf(126),
        difficulty = { combatLevel >= 40 }),
    MOSS_GIANT(plural = "moss giants",
        npcs = setOf(112, 1587, 1588, 1681),
        difficulty = { combatLevel >= 40 }),
    OGRE(plural = "ogres",
        npcs = setOf(
            114, 115, 374, 2044, 2045, 2046, 2047, 2048, 2049, 2050, 2051, 2052, 2053, 2054, 2055, 2056,
            2057, 2801, 3587
        ),
        difficulty = { combatLevel >= 40 }),
    ICE_WARRIOR(plural = "ice warriors",
        npcs = setOf(),
        difficulty = { combatLevel >= 45 }),
    ICE_GIANT(plural = "ice giants",
        npcs = setOf(),
        difficulty = { combatLevel >= 50 }),
    CROCODILE(plural = "crocodiles",
        npcs = setOf(),
        difficulty = { combatLevel >= 50 }),
    SHADOW_WARRIOR(plural = "shadow warriors",
        npcs = setOf(),
        difficulty = { combatLevel >= 60 }),
    LESSER_DEMON(plural = "lesser demons",
        npcs = setOf(82, 752),
        difficulty = { combatLevel >= 60 }),
    TROLL(plural = "trolls",
        npcs = setOf(
            391, 392, 393, 394, 395, 396, 1096, 1097, 1098, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108,
            1109, 1110, 1111, 1112, 1115, 1116, 1117, 1118, 1119, 1120, 1121, 1122, 1123, 1124, 1125, 1126,
            1127, 1128, 1129, 1130, 1131, 1132, 1133, 1134, 1138, 1556, 1560, 1561, 1562, 1563, 1564, 1565,
            1566, 1936, 1937, 1938, 1939, 1940, 1941, 1942
        ),
        difficulty = { combatLevel >= 60 }),
    WEREWOLF(plural = "werewolves",
        npcs = setOf(1030, 1031, 1032, 1033, 1034, 1035),
        difficulty = { combatLevel >= 60 }),
    BLUE_DRAGON(plural = "blue dragons",
        npcs = setOf(52, 55),
        difficulty = { combatLevel >= 65 }),
    FIRE_GIANT(plural = "fire giants",
        npcs = setOf(110, 1582, 1583, 1584, 1585),
        difficulty = { combatLevel >= 65 }),
    RED_DRAGON(plural = "red dragons",
        npcs = setOf(53, 1589, 3588),
        difficulty = { combatLevel >= 68 }),
    ELF(plural = "elves",
        npcs = setOf(1183, 1184, 2359, 2360, 2361, 2362),
        difficulty = { combatLevel >= 70 }),
    GREATER_DEMON(plural = "greater demons",
        npcs = setOf(83),
        difficulty = { combatLevel >= 70 }),
    DAGANNOTH(plural = "dagannoths",
        npcs = setOf(
            1338, 1339, 1340, 1341, 1342, 1343, 1347, 1351, 1352, 1353, 1354, 1355, 1356, 2454, 2455,
            2456, 2880, 2881, 2882, 2883, 2887, 2888, 3591
        ),
        difficulty = { combatLevel >= 75 }),
    HELLHOUND(plural = "hellhounds",
        npcs = setOf(49, 3586),
        difficulty = { combatLevel >= 75 }),
    BLACK_DEMON(plural = "black demons",
        npcs = setOf(84, 677),
        difficulty = { combatLevel >= 80 }),
    BLACK_DRAGON(plural = "black dragons",
        npcs = setOf(50, 54, 3376),
        difficulty = { combatLevel >= 80 }),
    IRON_DRAGON(plural = "iron dragons",
        npcs = setOf(1591),
        difficulty = { combatLevel >= 80 }),
    STEEL_DRAGON(plural = "steel dragons",
        npcs = setOf(1592, 3590),
        difficulty = { combatLevel >= 85 }),
    CRAWLING_HAND(
        level = 5,
        plural = "crawling hands",
        npcs = setOf(1648, 1649, 1650, 1651, 1652, 1653, 1654, 1655, 1656, 1657)
    ),
    CAVE_BUG(
        level = 7,
        plural = "cave bugs",
        npcs = setOf(1832)
    ),
    CAVE_CRAWLER(
        level = 10,
        plural = "cave crawlers",
        npcs = setOf(1600, 1601, 1602, 1603)
    ),
    BANSHEE(
        level = 15,
        plural = "banshees",
        npcs = setOf(1612)
    ),
    CAVE_SLIME(
        level = 17,
        plural = "cave slime",
        npcs = setOf(1831)
    ),
    ROCKSLUG(
        level = 20,
        plural = "rockslugs",
        npcs = setOf(1622, 1623)
    ),
    DESERT_LIZARD(
        level = 22,
        plural = "desert lizards",
        npcs = setOf(2804, 2805, 2806)
    ),
    COCKATRICE(
        level = 25,
        plural = "cockatrice",
        npcs = setOf(1620, 1621)
    ),
    PYREFIEND(
        level = 30,
        plural = "pyrefiends",
        npcs = setOf(1633, 1634, 1635, 1636)
    ),
    MOGRE(
        level = 32,
        plural = "mogres",
        npcs = setOf(2801)
    ),
    HARPIE_BUG_SWARM(
        level = 33,
        plural = "harpie bug swarms",
        npcs = setOf(3153)
    ),
    WALL_BEAST(
        level = 35,
        plural = "wall beasts",
        npcs = setOf(1827)
    ),
    KILLERWATT(
        level = 37,
        plural = "killerwatts",
        npcs = setOf(1616, 1617)
    ),
    BASILISK(level = 40,
        plural = "basilisks",
        npcs = setOf(1616, 1617),
        difficulty = { combatLevel >= 40 && defence.staticLevel >= 20 }),
    FEVER_SPIDER(
        level = 42,
        plural = "fever spiders",
        npcs = setOf(2850)
    ),
    INFERNAL_MAGE(
        level = 45,
        plural = "infernal mages",
        npcs = setOf(1643, 1644, 1645, 1646, 1647)
    ),
    BLOODVELD(level = 50,
        plural = "bloodveld",
        npcs = setOf(1618, 1619),
        difficulty = { combatLevel >= 50 }),
    JELLY(level = 52,
        plural = "jellies",
        npcs = setOf(1637, 1638, 1639, 1640, 1641, 1642),
        difficulty = { combatLevel >= 57 }),
    TUROTH(level = 55,
        plural = "turoth",
        npcs = setOf(1626, 1627, 1628, 1629, 1630, 1631, 1632),
        difficulty = { combatLevel >= 60 }),
    ZYGOMITE(level = 57,
        plural = "zygomites",
        npcs = setOf(3346, 3347),
        difficulty = { combatLevel >= 60 }),
    ABERRANT_SPECTRE(
        level = 60,
        plural = "aberrant spectres",
        npcs = setOf(1604, 1605, 1606, 1607)
    ),
    DUST_DEVIL(level = 65,
        plural = "dust devils",
        npcs = setOf(1624),
        difficulty = { combatLevel >= 70 }),
    KURASK(
        level = 70,
        plural = "kurask",

        npcs = setOf(1608, 1609)
    ),
    SKELETAL_WYVERN(plural = "skeletal wyverns",
        level = 72,
        npcs = setOf(3068, 3069, 3070, 3071),
        difficulty = { combatLevel >= 70 }),
    GARGOYLE(
        level = 75,
        plural = "gargoyles",
        npcs = setOf(1610, 1611)
    ),
    NECHRYAEL(
        level = 80,
        plural = "nechryael",
        npcs = setOf(1613)
    ),
    ABYSSAL_DEMON(
        level = 85,
        plural = "abyssal demons",
        npcs = setOf(1615)
    ),
    DARK_BEAST(
        level = 90,
        plural = "dark beasts",
        npcs = setOf(2783)
    );

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableSet.copyOf(values())
    }
}