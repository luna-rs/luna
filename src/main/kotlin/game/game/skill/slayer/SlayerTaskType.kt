package game.skill.slayer

import api.predef.*
import com.google.common.collect.ImmutableSet
import io.luna.game.model.mob.Player

/**
 * Native Slayer assignments and their exact NPC memberships, shared by kill credit and assignment-bound bot combat.
 * Memberships include verified revision-377 Slayer categories and existing Luna variants. Explicitly classified
 * forms count even when their display name differs from the assignment, such as human-form werewolves.
 * Enum names remain stable so saved assignments continue to resolve to the corrected memberships.
 *
 * @property level Minimum Slayer level used by assignment selection and bot readiness.
 * @property plural The assignment name used in dialogue.
 * @property npcs Exact NPC IDs whose kills count toward this assignment.
 * @property difficulty Additional account requirements when native difficulty checking is enabled.
 * @property tip The master's advice for this assignment.
 *
 * @author lare96
 * TODO@0.5.0 Add tips?
 */
enum class SlayerTaskType(val level: Int = 1,
                          val plural: String,
                          val npcs: Set<Int>,
                          val difficulty: Player.() -> Boolean = { true },
                          val tip: String = "Err... Well..? Have fun!") {
    MONKEY(plural = "monkeys",
           npcs = setOf(132, 1456, 1459, 1465, 1467, 1466),
           tip = "Monkeys are tricky creatures, they are agile and\n" +
                 "fairly fast. Learn to anticipate their movements."),
    ROCK_CRAB(plural = "rock crabs",
              npcs = setOf(1265, 1267, 2452, 2885)),
    SPIDER(plural = "spiders",
           npcs = setOf(58, 59, 60, 61, 62, 63, 64, 134, 1009, 1221, 1473, 1474, 2035, 2491, 2492, 2850, 3585, 997,
                        1004),
           tip = "Spiders are often poisonous, and many varieties are\n" +
                 "camouflaged too."),
    BIRD(plural = "birds",
         npcs = setOf(41, 44, 45, 48, 131, 136, 951, 1015, 1017, 1018, 1401, 1402, 1403, 1475, 1476, 1692, 1996, 2313,
                      2314, 2315, 2693, 2694, 3675, 3676, 46),
         tip = "Birds aren't the most intelligent of creatures, but\n" +
               "watch out for their sharp stabbing beaks."),
    RAT(plural = "rats",
        npcs = setOf(47, 86, 87, 88, 224, 446, 748, 950, 2032, 2033, 2682, 2722, 2723, 2981),
        tip = "Rats are your everyday pest, but they can get\n" +
              "fairly big. Watch out for their sharp piercing teeth."),
    GOBLIN(plural = "goblins",
           npcs = setOf(100, 101, 102, 298, 299, 444, 445, 1769, 1770, 1771, 1772, 1773, 1774, 1775, 1776, 1822, 1823,
                        1824, 1825, 2069, 2070, 2071, 2072, 2073, 2074, 2075, 2076, 2077, 2078, 2274, 2275, 2276, 2277,
                        2278, 2279, 2280, 2281, 2678, 2679, 2680, 2681, 3264, 3265, 3266, 3267),
           tip = "Goblins are mostly just annoying, but they can be\n" +
                 "vicious. Watch out for the spears they sometimes\n" +
                 "carry."),
    BAT(plural = "bats",
        npcs = setOf(78, 412),
        difficulty = { combatLevel >= 5 },
        tip = "Bats are rarely found on the ground, so you'll\n" +
              "have to fight them while they're airborne, which\n" +
              "won't be easy for melee."),
    COW(plural = "cows",
        npcs = setOf(81, 397, 955, 1767, 3309, 1691),
        difficulty = { combatLevel >= 5 },
        tip = "Cows are bigger than you, so they'll often hit fairly\n" +
              "hard but are usually fairly slow to react."),
    DWARF(plural = "dwarves",
          npcs = setOf(118, 119, 120, 121, 1795, 1796, 1797, 2130, 2131, 2132, 2133, 2134, 2135, 2136, 3219, 3220,
                       3221, 3268, 3269, 3270, 3271, 3272, 3273, 3274, 3275, 3276, 3277, 3278, 3279),
          difficulty = { combatLevel >= 6 },
          tip = "Dwarves are a small but tough race of miners,\n" +
                "often using pickaxes to pierce their opponents\n" +
                "armour."),
    SCORPION(plural = "scorpions",
             npcs = setOf(107, 108, 109, 144, 493, 1477, 1693),
             difficulty = { combatLevel >= 7 },
             tip = "Scorpions are almost always poisonous, their hard\n" +
                   "carapace makes them resistant to crushing and\n" +
                   "stabbing attacks."),
    ZOMBIE(plural = "zombies",
           npcs = setOf(73, 74, 75, 76, 419, 420, 421, 422, 423, 424, 504, 505, 751, 1465, 1466, 1467, 2044, 2045,
                        2046, 2047, 2048, 2049, 2051, 2052, 2053, 2054, 2055, 2058, 2837, 2838, 2839, 2840, 2841, 2842,
                        2843, 2844, 2845, 2846, 2847, 2848, 3622, 77),
           difficulty = { combatLevel >= 10 },
           tip = "Zombies are undead so magic is your best bet\n" +
                 "against them, there is even a spell specially for\n" +
                 "fighting the undead."),
    BEAR(plural = "bears",
         npcs = setOf(1195, 1196, 1197, 1326, 1327, 105, 106),
         difficulty = { combatLevel >= 13 },
         tip = "Bears are tough creatures and fierce fighters,\n" +
               "watch out for their powerful claws."),
    GHOST(plural = "ghosts",
          npcs = setOf(79, 103, 104, 491, 749, 1549, 1698, 1541, 2716, 2931, 1218),
          difficulty = { combatLevel >= 13 },
          tip = "Ghosts are undead so magic is your best bet\n" +
                "against them, there is even a spell specially for\n" +
                "fighting the undead."),
    DOG(plural = "dogs",
        npcs = setOf(99, 1047, 1593, 1594, 1976, 1994, 3582),
        difficulty = { combatLevel >= 15 },
        tip = "Dogs are much like Wolves, they are pack\n" +
              "creatures which will hunt in groups."),
    KALPHITE(plural = "kalphite",
             npcs = setOf(1153, 1154, 1155, 1156, 1157, 1158, 1160, 3589, 3835, 3836),
             difficulty = { combatLevel >= 15 },
             tip = "Kalphite are large insects which live in great hives\n" +
                   "under the desert sands."),
    SKELETON(plural = "skeletons",
             npcs = setOf(90, 91, 92, 93, 459, 750, 1471, 1515, 2036, 2037, 2050, 2056, 2057, 2724, 2725, 3291, 3581,
                          94, 502, 503),
             difficulty = { combatLevel >= 15 },
             tip = "Skeletons are undead so magic is your best bet\n" +
                   "against them, there is even a spell specially for\n" +
                   "fighting the undead."),
    ICEFIEND(plural = "icefiends",
             npcs = setOf(3406),
             difficulty = { combatLevel >= 20 },
             tip = "Icefiends are beings of ice and freezing rock,\n" +
                   "they're quick and agile so you'll want to be careful\n" +
                   "when getting close to them."),
    WOLF(plural = "wolves",
         npcs = setOf(95, 141, 142, 143, 839, 1198, 1330, 1558, 1559, 1951, 1952, 1953, 1954, 1955, 1956, 97),
         difficulty = { combatLevel >= 20 },
         tip = "Wolves are pack animals, so you'll always find them\n" +
               "in groups. Watch out for their bite, it can be\n" +
               "nasty."),
    HOBGOBLIN(plural = "hobgoblins",
              npcs = setOf(122, 123, 2685, 2686, 2687, 2688, 3583),
              difficulty = { combatLevel >= 20 },
              tip = "Hobgoblins are sneaky underhanded creatures, they\n" +
                    "often wield spears and some times carry javelines\n" +
                    "too."),
    LIZARD(level = 22,
           plural = "lizards",
           npcs = setOf(2803, 2804, 2805, 2806, 2807, 2808),
           tip = "Lizards are large reptiles with tough skin. Those\n" +
                 "found in the desert will need you to douse them\n" +
                 "with freezing water to finish them off after a tough\n" +
                 "battle."),
    GHOUL(plural = "ghouls",
          npcs = setOf(1218, 3059),
          difficulty = { combatLevel >= 25 },
          tip = "Ghouls aren't undead but they are stronger and\n" +
                "tougher than they look. However they're also very\n" +
                "cowardly and will run if they're losing a fight."),
    HILL_GIANT(plural = "hill giants",
               npcs = setOf(116, 117),
               difficulty = { combatLevel >= 25 },
               tip = "Hill Giants often wield large weapons, learn to\n" +
                     "recognise what kind of weapon it is and act\n" +
                     "accordingly."),
    SHADE(plural = "shades",
          npcs = setOf(425, 426, 427, 428, 429, 430, 1241, 1244, 1246, 1248, 1250, 3617),
          difficulty = { combatLevel >= 30 },
          tip = "Shades are undead so magic is your best bet\n" +
                "against them, you can find Shades at Mort'ton."),
    OTHERWORLDLY_BEING(plural = "otherworldly beings",
                       npcs = setOf(126),
                       difficulty = { combatLevel >= 40 },
                       tip = "Otherworldly Beings are ethereal beings making\n" +
                             "them weak to magical attack."),
    MOSS_GIANT(plural = "moss giants",
               npcs = setOf(112, 1587, 1588, 1681),
               difficulty = { combatLevel >= 40 },
               tip = "Like other giants, Moss Giants often wield large\n" +
                     "weapons, learn to recognise what kind of weapon it\n" +
                     "is and act accordingly."),
    OGRE(plural = "ogres",
         npcs = setOf(114, 115, 374, 2044, 2045, 2046, 2047, 2048, 2049, 2050, 2051, 2052, 2053, 2054, 2055, 2056,
                      2057, 2801, 3587),
         difficulty = { combatLevel >= 40 },
         tip = "Ogres are brutal creatures, favouring large blunt\n" +
               "maces and clubs, they often attack without warning."),
    ICE_WARRIOR(plural = "ice warriors",
                npcs = setOf(125, 3073),
                difficulty = { combatLevel >= 45 },
                tip = "Ice Warriors are a kind of ice elemental, shatter\n" +
                      "them with blunt weapons or melt them with fire."),
    ICE_GIANT(plural = "ice giants",
              npcs = setOf(111, 3072),
              difficulty = { combatLevel >= 50 },
              tip = "Like other giants, Ice Giants often wield large\n" +
                    "weapons, learn to recognise what kind of weapon it\n" +
                    "is and act accordingly."),
    CROCODILE(plural = "crocodiles",
              npcs = setOf(1993),
              difficulty = { combatLevel >= 50 },
              tip = "Crocodiles are large reptiles which live near water.\n" +
                    "You'll want to have a stabbing weapon handy for\n" +
                    "puncturing their thick scaly hides."),
    SHADOW_WARRIOR(plural = "shadow warriors",
                   npcs = setOf(158),
                   difficulty = { combatLevel >= 60 },
                   tip = "Shadow Warriors are dark and mysterious, they\n" +
                         "hide in the shadows so be wary of ambushes."),
    LESSER_DEMON(plural = "lesser demons",
                 npcs = setOf(82, 752),
                 difficulty = { combatLevel >= 60 },
                 tip = "Lesser Demons are magic creatures so they are\n" +
                       "weak to magical attacks. Though they're relatively\n" +
                       "weak they are still dangerous."),
    TROLL(plural = "trolls",
          npcs = setOf(391, 392, 393, 394, 395, 396, 1096, 1097, 1098, 1101, 1102, 1103, 1104, 1105, 1106, 1107, 1108,
                       1109, 1110, 1111, 1112, 1115, 1116, 1117, 1118, 1119, 1120, 1121, 1122, 1123, 1124, 1125, 1126,
                       1127, 1128, 1129, 1130, 1131, 1132, 1133, 1134, 1138, 1556, 1560, 1561, 1562, 1563, 1564, 1565,
                       1566, 1936, 1937, 1938, 1939, 1940, 1941, 1942, 72),
          difficulty = { combatLevel >= 60 },
          tip = "Trolls regenerate damage quickly but are still\n" +
                "vulnerable to poisons, they usually use crushing\n" +
                "weapons."),
    WEREWOLF(plural = "werewolves",
             npcs = setOf(1030, 1031, 1032, 1033, 1034, 1035, 1024, 1025, 1026, 1027, 1028, 1029),
             difficulty = { combatLevel >= 60 },
             tip = "Werewolves are feral creatures, they are strong\n" +
                   "and tough with sharp claws and teeth."),
    BLUE_DRAGON(plural = "blue dragons",
                npcs = setOf(52, 55),
                difficulty = { combatLevel >= 65 },
                tip = "Blue Dragons aren't as strong as other dragons\n" +
                      "but they're still very powerful, watch out for their\n" +
                      "fiery breath."),
    FIRE_GIANT(plural = "fire giants",
               npcs = setOf(110, 1582, 1583, 1584, 1585),
               difficulty = { combatLevel >= 65 },
               tip = "Like other giants, Fire Giants often wield large\n" +
                     "weapons, learn to recognise what kind of weapon it\n" +
                     "is and act accordingly."),
    RED_DRAGON(plural = "red dragons",
               npcs = setOf(53, 1589, 3588),
               difficulty = { combatLevel >= 68 },
               tip = "Red Dragons are very powerful, stronger than\n" +
                     "most dragons, watch out for their fiery breath."),
    ELF(plural = "elves",
        npcs = setOf(1183, 1184, 2359, 2360, 2361, 2362),
        difficulty = { combatLevel >= 70 },
        tip = "Elves are quick, agile, and vicious fighters which\n" +
              "often favour bows and polearms."),
    GREATER_DEMON(plural = "greater demons",
                  npcs = setOf(83),
                  difficulty = { combatLevel >= 70 },
                  tip = "Greater Demons are magic creatures so they are\n" +
                        "weak to magical attacks. Though not the strongest\n" +
                        "demon, they are still dangerous."),
    DAGANNOTH(plural = "dagannoths",
              npcs = setOf(1338, 1339, 1340, 1341, 1342, 1343, 1347, 1351, 1352, 1353, 1354, 1355, 1356, 2454, 2455,
                           2456, 2880, 2881, 2882, 2883, 2887, 2888, 3591, 1344, 1345, 1346),
              difficulty = { combatLevel >= 75 },
              tip = "Dagannoth are large sea dwelling creatures which\n" +
                    "are very aggressive. You'll often find them in caves\n" +
                    "near sea water."),
    HELLHOUND(plural = "hellhounds",
              npcs = setOf(49, 3586),
              difficulty = { combatLevel >= 75 },
              tip = "Hellhounds are a cross between Dogs and Demons,\n" +
                    "they are dangerous with a fierce bite."),
    BLACK_DEMON(plural = "black demons",
                npcs = setOf(84, 677, 911),
                difficulty = { combatLevel >= 80 },
                tip = "Black Demons are magic creatures so they are\n" +
                      "weak to magical attacks. They're a very strong\n" +
                      "demon and very dangerous."),
    BLACK_DRAGON(plural = "black dragons",
                 npcs = setOf(50, 54, 3376),
                 difficulty = { combatLevel >= 80 },
                 tip = "Black Dragons are the strongest dragons and very\n" +
                       "fierce, watch out for their fiery breath."),
    IRON_DRAGON(plural = "iron dragons",
                npcs = setOf(1591),
                difficulty = { combatLevel >= 80 },
                tip = "Iron Dragons are some of the weaker metallic\n" +
                      "dragons, their iron scales are far thicker than\n" +
                      "normal iron armour."),
    STEEL_DRAGON(plural = "steel dragons",
                 npcs = setOf(1592, 3590),
                 difficulty = { combatLevel >= 85 },
                 tip = "Steel Dragons are dangerous metallic dragons, their\n" +
                       "steel scales are far thicker than normal steel\n" +
                       "armour."),
    CRAWLING_HAND(level = 5,
                  plural = "crawling hands",
                  npcs = setOf(1648, 1649, 1650, 1651, 1652, 1653, 1654, 1655, 1656, 1657),
                  tip = "Crawling Hands are undead severed hands, fast\n" +
                        "and dexterous they claw their victims."),
    CAVE_BUG(level = 7,
             plural = "cave bugs",
             npcs = setOf(1832),
             tip = "Cave Bugs are like Cave Crawlers, except smaller\n" +
                   "and easier to squish, though they still have a\n" +
                   "fondness for plants."),
    CAVE_CRAWLER(level = 10,
                 plural = "cave crawlers",
                 npcs = setOf(1600, 1601, 1602, 1603),
                 tip = "Cave Crawlers are small and fast, often hiding in\n" +
                       "ambush. Avoid their barbed tongue or you'll get\n" +
                       "poisoned."),
    BANSHEE(level = 15,
            plural = "banshees",
            npcs = setOf(1612),
            tip = "Banshees use a piercing scream to shock their\n" +
                  "enemies, you'll need some Earmuffs to protect\n" +
                  "yourself from them."),
    CAVE_SLIME(level = 17,
               plural = "cave slime",
               npcs = setOf(1831),
               tip = "Cave Slimes are the lesser cousins of Jellies, though\n" +
                     "don't be fooled they can still be dangerous as\n" +
                     "they're often poisonous."),
    ROCKSLUG(level = 20,
             plural = "rockslugs",
             npcs = setOf(1622, 1623),
             tip = "Rockslugs are strange stoney slugs. You'll need to\n" +
                   "fight them to near death before finishing them off\n" +
                   "with Salt."),
    DESERT_LIZARD(level = 22,
                  plural = "desert lizards",
                  npcs = setOf(2804, 2805, 2806, 2803, 2807, 2808),
                  tip = "Lizards are large reptiles with tough skin. Those\n" +
                        "found in the desert will need you to douse them\n" +
                        "with freezing water to finish them off after a tough\n" +
                        "battle."),
    COCKATRICE(level = 25,
               plural = "cockatrice",
               npcs = setOf(1620, 1621),
               tip = "Cockatrice, like Basilisks, have a gaze which will\n" +
                     "paralyse and harm their prey. You'll need a Mirror\n" +
                     "Shield to protect you."),
    PYREFIEND(level = 30,
              plural = "pyrefiends",
              npcs = setOf(1633, 1634, 1635, 1636),
              tip = "Pyrefiends are beings of fire and molten rock,\n" +
                    "they're quick and agile so you'll want to be careful\n" +
                    "when getting close to them."),
    MOGRE(level = 32,
          plural = "mogres",
          npcs = setOf(2801),
          tip = "Mogres are a type of aquatic Ogre that is often\n" +
                "mistaken for a giant mudskipper. You have to force\n" +
                "them out of the water with a fishing explosive."),
    HARPIE_BUG_SWARM(level = 33,
                     plural = "harpie bug swarms",
                     npcs = setOf(3153),
                     tip = "Harpie Bug Swarms are pesky critters that are\n" +
                           "hard to hit. You need a lit bug lantern to distract\n" +
                           "them with its hypnotic light."),
    WALL_BEAST(level = 35,
               plural = "wall beasts",
               npcs = setOf(1827),
               tip = "Wall Beasts are really much larger creatures but\n" +
                     "you'll only see their arms. You'll want something\n" +
                     "sharp on your head to stop them grabbing you."),
    KILLERWATT(level = 37,
               plural = "killerwatts",
               npcs = setOf(3201, 3202),
               tip = "Killerwatts store huge amounts of energy in their\n" +
                     "bodies, which is released if they are touched. You'll\n" +
                     "need to wear heavily insulated boots to counter this\n" +
                     "shocking effect."),
    BASILISK(level = 40,
             plural = "basilisks",
             npcs = setOf(1616, 1617),
             difficulty = { combatLevel >= 40 && defence.staticLevel >= 20 },
             tip = "Basilisks, like Cockatrice, have a gaze which will\n" +
                   "paralyse and harm their prey. You'll need a Mirror\n" +
                   "Shield to protect you."),
    FEVER_SPIDER(level = 42,
                 plural = "fever spiders",
                 npcs = setOf(2850),
                 tip = "Fever Spiders are giant spiders that carry the\n" +
                       "deadly Spider Fever. If you don't want to catch it\n" +
                       "I suggest you wear Slayer Gloves to fight them."),
    INFERNAL_MAGE(level = 45,
                  plural = "infernal mages",
                  npcs = setOf(1643, 1644, 1645, 1646, 1647),
                  tip = "Infernal Mages are dangerous spell users, beware\n" +
                        "of their magic spells and go properly prepared."),
    BLOODVELD(level = 50,
              plural = "bloodveld",
              npcs = setOf(1618, 1619),
              difficulty = { combatLevel >= 50 },
              tip = "Bloodveld are strange demonic creatures, they use\n" +
                    "their long rasping tongue to feed on just about\n" +
                    "anything they can find."),
    JELLY(level = 52,
          plural = "jellies",
          npcs = setOf(1637, 1638, 1639, 1640, 1641, 1642),
          difficulty = { combatLevel >= 57 },
          tip = "Jellies are nasty cube-like gelatinous creatures\n" +
                "which absorb everything they come across into\n" +
                "themselves."),
    TUROTH(level = 55,
           plural = "turoth",
           npcs = setOf(1626, 1627, 1628, 1629, 1630, 1631, 1632),
           difficulty = { combatLevel >= 60 },
           tip = "Turoth are large vicious creatures with thick hides.\n" +
                 "You'll need a Leaf-Tipped Spear, Broad Arrows, or\n" +
                 "a Magic Dart to harm them."),
    ZYGOMITE(level = 57,
             plural = "zygomites",
             npcs = setOf(3346, 3347),
             difficulty = { combatLevel >= 60 },
             tip = "Mutated Zygomites are hard to destroy. They\n" +
                   "regenerate quickly so you will need to finish them\n" +
                   "with fungicide."),
    ABERRANT_SPECTRE(level = 60,
                     plural = "aberrant spectres",
                     npcs = setOf(1604, 1605, 1606, 1607),
                     tip = "Aberrant Specters are fetid, vile ghosts. The very\n" +
                           "smell of them will paralyse and harm you. A\n" +
                           "nosepeg will help ignore their stink."),
    DUST_DEVIL(level = 65,
               plural = "dust devils",
               npcs = setOf(1624),
               difficulty = { combatLevel >= 70 },
               tip = "Dust Devils use clouds of dust, sand, ash, and\n" +
                     "whatever else they can inhale to blind and\n" +
                     "disorientate their victims."),
    KURASK(level = 70,
           plural = "kurask",

           npcs = setOf(1608, 1609),
           tip = "Kurask are large brutal creatures with very thick\n" +
                 "hides. You'll need a Leaf-Tipped Spear, Broad\n" +
                 "Arrows, or a Magic Dart to harm them."),
    SKELETAL_WYVERN(plural = "skeletal wyverns",
                    level = 72,
                    npcs = setOf(3068, 3069, 3070, 3071),
                    difficulty = { combatLevel >= 70 },
                    tip = "Skeletal Wyverns are extremely dangerous and\n" +
                          "they are hard to hit with arrows as they slip right\n" +
                          "through. To stand a good chance of surviving you'll\n" +
                          "need some elemental shielding from its icy breath."),
    GARGOYLE(level = 75,
             plural = "gargoyles",
             npcs = setOf(1610, 1611),
             tip = "Gargoyles are winged creatures of stone. You'll\n" +
                   "need to fight them to near death before breaking\n" +
                   "them apart with a Rock Hammer."),
    NECHRYAEL(level = 80,
              plural = "nechryael",
              npcs = setOf(1613),
              tip = "Nechryael are demons of decay which summon\n" +
                    "small winged beings to help them fight their victims."),
    ABYSSAL_DEMON(level = 85,
                  plural = "abyssal demons",
                  npcs = setOf(1615),
                  tip = "Abyssal Demons are nasty creatures to fight. They\n" +
                        "aren't really part of this realm, and are able to\n" +
                        "move very quickly to trap their prey."),
    DARK_BEAST(level = 90,
               plural = "dark beasts",
               npcs = setOf(2783),
               tip = "Dark Beasts are large, dog-like predators. Their\n" +
                     "massively muscled bodies protect them from\n" +
                     "crushing weapons.");

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableSet.copyOf(values())
    }
}
