package world.player.skill.slayer

import api.predef.*
import com.google.common.collect.ImmutableSet
import io.luna.game.model.mob.Player

// TODO finish
enum class SlayerTaskType(val level: Int = 1,
                          val plural: String = "", // TODO remove default value
                          val npcXp: Map<Int, Double>,
                          val difficulty: Player.() -> Boolean = { true },
                          val tip: String = "Err... Well..? Have fun!") {
    MONKEY(plural = "monkeys",
           npcXp = mapOf(
               132 to 6.0,
               1456 to 50.0,
               1459 to 130.0,
               1465 to 60.0,
               1467 to 60.0,
               1466 to 90.0
           )),
    ROCK_CRAB(plural = "rock crabs",
              npcXp = mapOf(
                  1265 to 50.0,
                  1267 to 50.0,
                  2452 to 198.0,
                  2885 to 198.0
              )),
    SPIDER(plural = "spiders",
           npcXp = mapOf(
               61 to 2.0,
               1004 to 2.0,
               1473 to 2.0,
               1474 to 2.0,
               977 to 32.0,
               2034 to 45.0,
               2035 to 45.0,
               63 to 35.0,
               2850 to 40.0,
               2035 to 80.0,
               59 to 5.0,
               60 to 33.0,
               64 to 65.0,
               62 to 50.0,
               1478 to 50.0,
               2491 to 50.0,
               2492 to 50.0,
               134 to 64.0,
               1009 to 64.0,
               58 to 55.0
           )),
    BIRD(plural = "birds",
         npcXp = mapOf(
             44 to 3.0,
             45 to 3.0,
             41 to 3.0,
             951 to 3.0,
             1017 to 3.0,
             1692 to 3.0,
             1018 to 5.0,
             2312 to 5.0,
             1403 to 5.0,
             2693 to 6.0,
             2694 to 10.0,
             131 to 4.0,
             1475 to 10.0,
             1476 to 5.0,
             1521 to 5.0,
             1522 to 5.0,
             136 to 34.0,
             1015 to 10.0,
             1016 to 10.0,
             1996 to 10.0,
             3675 to 10.0,
             3676 to 10.0,
             48 to 40.0,
             137 to 36.0,
             138 to 55.0,
             2252 to 55.0
         )),
    RAT(plural = "rats",
        npcXp = mapOf(
            47 to 2.0,
            2682 to 2.0,
            86 to 5.0,
            87 to 10.0,
            446 to 5.0,
            748 to 5.0,
            950 to 5.0,
            2722 to 5.0,
            2723 to 5.0,
            2032 to 45.0,
            2033 to 70.0
        )),
    GOBLIN(plural = "goblins",
           npcXp = mapOf(
               100 to 5.0,
               101 to 5.0,
               102 to 16.0,
               298 to 5.0,
               299 to 5.0,
               444 to 5.0,
               445 to 5.0,
               1769 to 5.0,
               1770 to 5.0,
               1771 to 5.0,
               1772 to 5.0,
               1773 to 5.0,
               1774 to 5.0,
               1775 to 5.0,
               1776 to 5.0,
               2274 to 5.0,
               2275 to 5.0,
               2276 to 5.0,
               2277 to 5.0,
               2278 to 5.0,
               2279 to 5.0,
               2280 to 5.0,
               2281 to 5.0,
               2678 to 5.0,
               2679 to 5.0,
               2680 to 5.0,
               2681 to 5.0,
               3264 to 5.0,
               3265 to 5.0,
               3266 to 5.0,
               3267 to 5.0,
               1822 to 10.0,
               1823 to 10.0,
               1824 to 10.0,
               1825 to 10.0,
               2069 to 10.0,
               2070 to 10.0,
               2071 to 10.0,
               2072 to 10.0,
               2075 to 10.0,
               2076 to 10.0,
               2077 to 10.0,
               2078 to 10.0,
               2073 to 26.0,
               2074 to 26.0
           )),
    BAT(plural = "bats",
        npcXp = mapOf(
            412 to 8.0,
            78 to 32.0,
            79 to 80.0
        ),
        difficulty = { combatLevel >= 5 }),
    COW(plural = "cows",
        npcXp = mapOf(
            81 to 8.0,
            397 to 8.0,
            955 to 8.0,
            1767 to 8.0,
            3309 to 8.0
        ),
        difficulty = { combatLevel >= 5 }),
    DWARF(plural = "dwarves",
          npcXp = mapOf(
              118 to 16.0,
              120 to 26.0,
              121 to 16.0,
              3219 to 16.0,
              3220 to 16.0,
              3221 to 16.0,
              3268 to 16.0,
              3269 to 16.0,
              3270 to 16.0,
              3271 to 16.0,
              3272 to 16.0,
              3273 to 16.0,
              3274 to 16.0,
              3275 to 16.0,
              2130 to 40.0,
              2131 to 40.0,
              2132 to 40.0,
              2133 to 40.0,
              3276 to 30.0,
              3277 to 30.0,
              3278 to 30.0,
              3279 to 30.0,
              1795 to 40.0,
              1796 to 25.0,
              1797 to 25.0,
              119 to 61.0,
              2423 to 61.0,
              2134 to 50.0,
              2135 to 50.0,
              2136 to 50.0
          ),
          difficulty = { combatLevel >= 6 }),
    SCORPION(plural = "scorpions",
             npcXp = mapOf(
                 493 to 7.0,
                 107 to 17.0,
                 1477 to 15.0,
                 108 to 23.0,
                 109 to 32.0,
                 144 to 30.0,
                 1693 to 32.0
             ),
             difficulty = { combatLevel >= 7 }),
    ZOMBIE(plural = "zombies",
           npcXp = mapOf(
               73 to 22.1,
               74 to 30.6,
               75 to 40.8,
               76 to 42.5,
               419 to 23.8,
               420 to 49.3,
               421 to 83.3,
               422 to 134.3,
               423 to 204.0,
               424 to 270.3,
               502 to 47.0,
               503 to 47.0,
               504 to 47.0,
               505 to 59.0,
               751 to 40.8,
               1465 to 60.0,
               1466 to 90.0,
               1467 to 60.0,
               1691 to 8.0,
               1692 to 3.0,
               2044 to 71.0,
               2045 to 71.0,
               2046 to 71.0,
               2047 to 71.0,
               2048 to 71.0,
               2049 to 71.0,
               2051 to 71.0,
               2052 to 71.0,
               2053 to 71.0,
               2054 to 71.0,
               2055 to 71.0,
               2058 to 66.3,
               2837 to 50.0,
               2838 to 50.0,
               2839 to 50.0,
               2840 to 50.0,
               2841 to 50.0,
               2842 to 50.0,
               2843 to 50.0,
               2844 to 50.0,
               2845 to 50.0,
               2846 to 50.0,
               2847 to 50.0,
               2848 to 50.0,
               3622 to 39.1
           ),
           difficulty = { combatLevel >= 10 }),
    BEAR(plural = "bears",
         npcXp = mapOf(
             1195 to 35.0,
             1196 to 35.0,
             1197 to 35.0,
             1326 to 20.0,
             1327 to 20.0
         ),
         difficulty = { combatLevel >= 13 }),
    GHOST(plural = "ghosts",
          npcXp = mapOf(
              79 to 80.0,
              103 to 25.0,
              104 to 25.0,
              491 to 20.0,
              749 to 25.0,
              1541 to 30.0,
              1549 to 30.0,
              1698 to 51.0,
              2716 to 30.0,
              2931 to 30.0
          ),
          difficulty = { combatLevel >= 13 }),
    DOG(plural = "dogs",
        npcXp = mapOf(
            99 to 49.0,
            1047 to 45.0,
            1593 to 62.0,
            1594 to 62.0,
            1976 to 62.0,
            1994 to 27.0,
            3582 to 49.0
        ),
        difficulty = { combatLevel >= 15 }),
    KALPHITE(plural = "kalphite",
             npcXp = mapOf(
                 1153 to 40.0,
                 1155 to 170.0,
                 1156 to 40.0,
                 1157 to 170.0,
                 1158 to 535.5,
                 1159 to 535.5,
                 1160 to 535.5,
                 3835 to 535.5,
                 3836 to 535.5
             ),
             difficulty = { combatLevel >= 15 }),
    SKELETON(plural = "skeletons",
             npcXp = mapOf(

             ),
             difficulty = { combatLevel >= 15 }),
    ICEFIEND(plural = "icefiends",
             npcXp = mapOf(

             ),
             difficulty = { combatLevel >= 20 }),
    WOLF(plural = "wolves",
         npcXp = mapOf(),
         difficulty = { combatLevel >= 20 }),
    HOBGOBLIN(plural = "hobgoblins",
              npcXp = mapOf(),
              difficulty = { combatLevel >= 20 }),
    LIZARD(plural = "lizards",
           npcXp = mapOf(),
           difficulty = { slayer.level >= 22 }),
    GHOUL(plural = "ghouls",
          npcXp = mapOf(),
          difficulty = { combatLevel >= 25 }),
    HILL_GIANT(plural = "hill giants",
               npcXp = mapOf(),
               difficulty = { combatLevel >= 25 }),
    SHADE(plural = "shades",
          npcXp = mapOf(),
          difficulty = { combatLevel >= 30 }),
    OTHERWORLDLY_BEING(plural = "otherworldly beings",
                       npcXp = mapOf(),
                       difficulty = { combatLevel >= 40 }),
    MOSS_GIANT(plural = "moss giants",
               npcXp = mapOf(),
               difficulty = { combatLevel >= 40 }),
    OGRE(plural = "ogres",
         npcXp = mapOf(),
         difficulty = { combatLevel >= 40 }),
    ICE_WARRIOR(plural = "ice warriors",
                npcXp = mapOf(),
                difficulty = { combatLevel >= 45 }),
    ICE_GIANT(plural = "ice giants",
              npcXp = mapOf(),
              difficulty = { combatLevel >= 50 }),
    CROCODILE(plural = "crocodiles",
              npcXp = mapOf(),
              difficulty = { combatLevel >= 50 }),
    SHADOW_WARRIOR(plural = "shadow warriors",
                   npcXp = mapOf(),
                   difficulty = { combatLevel >= 60 }),
    LESSER_DEMON(plural = "lesser demons",
                 npcXp = mapOf(),
                 difficulty = { combatLevel >= 60 }),
    TROLL(plural = "trolls",
          npcXp = mapOf(),
          difficulty = { combatLevel >= 60 }),
    WEREWOLF(plural = "werewolves",
             npcXp = mapOf(),
             difficulty = { combatLevel >= 60 }),
    BLUE_DRAGON(plural = "blue dragons",
                npcXp = mapOf(),
                difficulty = { combatLevel >= 65 }),
    FIRE_GIANT(plural = "fire giants",
               npcXp = mapOf(),
               difficulty = { combatLevel >= 65 }),
    RED_DRAGON(plural = "red dragons",
               npcXp = mapOf(),
               difficulty = { combatLevel >= 68 }),
    ELF(plural = "elves",
        npcXp = mapOf(),
        difficulty = { combatLevel >= 70 }),
    GREATER_DEMON(plural = "greater demons",
                  npcXp = mapOf(),
                  difficulty = { combatLevel >= 70 }),
    DAGANNOTH(plural = "dagannoths",
              npcXp = mapOf(),
              difficulty = { combatLevel >= 75 }),
    HELLHOUND(plural = "hellhounds",
              npcXp = mapOf(),
              difficulty = { combatLevel >= 75 }),
    BLACK_DEMON(plural = "black demons",
                npcXp = mapOf(),
                difficulty = { combatLevel >= 80 }),
    BLACK_DRAGON(plural = "black dragons",
                 npcXp = mapOf(),
                 difficulty = { combatLevel >= 80 }),
    IRON_DRAGON(plural = "iron dragons",
                npcXp = mapOf(),
                difficulty = { combatLevel >= 80 }),
    STEEL_DRAGON(plural = "steel dragons",
                 npcXp = mapOf(),
                 difficulty = { combatLevel >= 85 }),
    CRAWLING_HAND(level = 5,
                  plural = "crawling hands",
                  npcXp = mapOf(

                  )),
    CAVE_BUG(level = 7,
             plural = "cave bugs",
             npcXp = mapOf(

             )),
    CAVE_CRAWLER(level = 10,
                 npcXp = mapOf(

                 )),
    BANSHEE(level = 15,
            npcXp = mapOf(

            )),
    CAVE_SLIME(level = 17,
               npcXp = mapOf(

               )),
    ROCKSLUG(level = 20,
             npcXp = mapOf(

             )),
    DESERT_LIZARD(plural = "desert lizards",
                  level = 22,
                  npcXp = mapOf(

                  )),
    COCKATRICE(level = 25,
               npcXp = mapOf(

               )),
    PYREFIEND(level = 30,
              npcXp = mapOf(

              )),
    MOGRE(level = 32,
          npcXp = mapOf(

          )),
    HARPIE_BUG_SWARM(level = 33,
                     npcXp = mapOf(

                     )),
    WALL_BEAST(level = 35,
               npcXp = mapOf(

               )),
    KILLERWATT(level = 37,
               npcXp = mapOf(

               )),
    BASILISK(level = 40,
             npcXp = mapOf(

             ),
             difficulty = { combatLevel >= 40 && defence.staticLevel >= 20 }),
    FEVER_SPIDER(level = 42,
                 npcXp = mapOf(

                 )),
    INFERNAL_MAGE(level = 45,
                  npcXp = mapOf(

                  )),
    BLOODVELD(level = 50,
              npcXp = mapOf(

              ),
              difficulty = { combatLevel >= 50 }),
    JELLY(level = 52,
          npcXp = mapOf(

          ),
          difficulty = { combatLevel >= 57 }),
    TUROTH(level = 55,
           npcXp = mapOf(

           ),
           difficulty = { combatLevel >= 60 }),
    ZYGOMITE(level = 57,
             npcXp = mapOf(

             ),
             difficulty = { combatLevel >= 60 }),
    ABERRANT_SPECTRE(level = 60,
                     npcXp = mapOf(

                     )),
    DUST_DEVIL(plural = "dust devils",
               level = 65,
               npcXp = mapOf(

               ),
               difficulty = { combatLevel >= 70 }),
    KURASK(plural = "kurask",
           level = 70,
           npcXp = mapOf(

           )),
    SKELETAL_WYVERN(plural = "skeletal wyverns",
                    level = 72,
                    npcXp = mapOf(

                    ),
                    difficulty = { combatLevel >= 70 }),
    GARGOYLE(plural = "gargoyles",
             level = 75,
             npcXp = mapOf(

             )),
    NECHRYAEL(plural = "nechryael",
              level = 80,
              npcXp = mapOf(

              )),
    ABYSSAL_DEMON(plural = "abyssal demons",
                  level = 85,
                  npcXp = mapOf(

                  )),
    DARK_BEAST(level = 90,
               npcXp = mapOf(

               ));

    companion object {

        /**
         * An immutable copy of [values].
         */
        val VALUES = ImmutableSet.copyOf(values())
    }
}