package world.player.skill.slayer

import api.predef.*
import com.google.common.collect.ImmutableSet
import io.luna.game.model.mob.Player

enum class SlayerTask(val level: Int = 1,
                      val plural: String = "",
                      val npcs: Map<Int, Double>,
                      val unlocked: Player.() -> Boolean = { true }) {
    MONKEY(plural = "monkeys",
           npcs = mapOf(
               132 to 6.0,
               1456 to 50.0,
               1459 to 130.0,
               1465 to 60.0,
               1467 to 60.0,
               1466 to 90.0
           )),
    ROCK_CRAB(plural = "rock crabs",
              npcs = mapOf(
                  1265 to 50.0,
                  1267 to 50.0,
                  2452 to 198.0,
                  2885 to 198.0
              )),
    SPIDER(plural = "spiders",
           npcs = mapOf(
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
         npcs = mapOf(
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
        npcs = mapOf(
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
           npcs = mapOf(
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
        npcs = mapOf(
            412 to 8.0,
            78 to 32.0,
            79 to 80.0,
        ),
        unlocked = { combatLevel >= 5 }),
    COW(plural = "cows",
        npcs = mapOf(
            81 to 8.0,
            397 to 8.0,
            955 to 8.0,
            1767 to 8.0,
            3309 to 8.0
        ),
        unlocked = { combatLevel >= 5 }),
    DWARF(plural = "dwarves",
          npcs = mapOf(
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
              2136 to 50.0,
          ),
          unlocked = { combatLevel >= 6 }),
    SCORPION(plural = "scorpions",
             npcs = mapOf(
                 493 to 7.0,
                 107 to 17.0,
                 1477 to 15.0,
                 108 to 23.0,
                 109 to 32.0,
                 144 to 30.0,
                 1693 to 32.0
             ),
             unlocked = { combatLevel >= 7 }),
    ZOMBIE(plural = "zombies",
           npcs = mapOf(

           ),
           unlocked = { combatLevel >= 10 }),
    BEAR(plural = "bears",
         npcs = mapOf(),
         unlocked = { combatLevel >= 13 }),
    GHOST(plural = "ghosts",
          npcs = mapOf(),
          unlocked = { combatLevel >= 13 }),
    DOG(plural = "dogs",
        npcs = mapOf(),
        unlocked = { combatLevel >= 15 }),
    KALPHITE(plural = "kalphite",
             npcs = mapOf(),
             unlocked = { combatLevel >= 15 }),
    SKELETON(plural = "skeletons",
             npcs = mapOf(),
             unlocked = { combatLevel >= 15 }),
    ICEFIEND(plural = "icefiends",
             npcs = mapOf(),
             unlocked = { combatLevel >= 20 }),
    WOLF(plural = "wolves",
         npcs = mapOf(),
         unlocked = { combatLevel >= 20 }),
    HOBGOBLIN(plural = "hobgoblins",
              npcs = mapOf(),
              unlocked = { combatLevel >= 20 }),
    LIZARD(plural = "lizards",
           npcs = mapOf(),
           unlocked = { slayer.level >= 22 }),
    GHOUL(plural = "ghouls",
          npcs = mapOf(),
          unlocked = { combatLevel >= 25 }),
    HILL_GIANT(plural = "hill giants",
               npcs = mapOf(),
               unlocked = { combatLevel >= 25 }),
    SHADE(plural = "shades",
          npcs = mapOf(),
          unlocked = { combatLevel >= 30 }),
    OTHERWORLDLY_BEING(plural = "otherworldly beings",
                       npcs = mapOf(),
                       unlocked = { combatLevel >= 40 }),
    MOSS_GIANT(plural = "moss giants",
               npcs = mapOf(),
               unlocked = { combatLevel >= 40 }),
    OGRE(plural = "ogres",
         npcs = mapOf(),
         unlocked = { combatLevel >= 40 }),
    ICE_WARRIOR(plural = "ice warriors",
                npcs = mapOf(),
                unlocked = { combatLevel >= 45 }),
    ICE_GIANT(plural = "ice giants",
              npcs = mapOf(),
              unlocked = { combatLevel >= 50 }),
    CROCODILE(plural = "crocodiles",
              npcs = mapOf(),
              unlocked = { combatLevel >= 50 }),
    SHADOW_WARRIOR(plural = "shadow warriors",
                   npcs = mapOf(),
                   unlocked = { combatLevel >= 60 }),
    LESSER_DEMON(plural = "lesser demons",
                 npcs = mapOf(),
                 unlocked = { combatLevel >= 60 }),
    TROLL(plural = "trolls",
          npcs = mapOf(),
          unlocked = { combatLevel >= 60 }),
    WEREWOLF(plural = "werewolves",
             npcs = mapOf(),
             unlocked = { combatLevel >= 60 }),
    BLUE_DRAGON(plural = "blue dragons",
                npcs = mapOf(),
                unlocked = { combatLevel >= 65 }),
    FIRE_GIANT(plural = "fire giants",
               npcs = mapOf(),
               unlocked = { combatLevel >= 65 }),
    RED_DRAGON(plural = "red dragons",
               npcs = mapOf(),
               unlocked = { combatLevel >= 68 }),
    ELF(plural = "elves",
        npcs = mapOf(),
        unlocked = { combatLevel >= 70 }),
    GREATER_DEMON(plural = "greater demons",
                  npcs = mapOf(),
                  unlocked = { combatLevel >= 70 }),
    DAGANNOTH(plural = "dagannoths",
              npcs = mapOf(),
              unlocked = { combatLevel >= 75 }),
    HELLHOUND(plural = "hellhounds",
              npcs = mapOf(),
              unlocked = { combatLevel >= 75 }),
    BLACK_DEMON(plural = "black demons",
                npcs = mapOf(),
                unlocked = { combatLevel >= 80 }),
    BLACK_DRAGON(plural = "black dragons",
                 npcs = mapOf(),
                 unlocked = { combatLevel >= 80 }),
    IRON_DRAGON(plural = "iron dragons",
                npcs = mapOf(),
                unlocked = { combatLevel >= 80 }),
    STEEL_DRAGON(plural = "steel dragons",
                 npcs = mapOf(),
                 unlocked = { combatLevel >= 85 }),
    CRAWLING_HAND(level = 5,
                  plural = "crawling hands",
                  npcs = mapOf(

                  )),
    CAVE_BUG(level = 7,
             npcs = mapOf(

             )),
    CAVE_CRAWLER(level = 10,
                 npcs = mapOf(

                 )),
    BANSHEE(level = 15,
            npcs = mapOf(

            )),
    CAVE_SLIME(level = 17,
               npcs = mapOf(

               )),
    ROCKSLUG(level = 20,
             npcs = mapOf(

             )),
    DESERT_LIZARD(level = 22,
                  npcs = mapOf(

                  )),
    COCKATRICE(level = 25,
               npcs = mapOf(

               )),
    PYREFIEND(level = 30,
              npcs = mapOf(

              )),
    MOGRE(level = 32,
          npcs = mapOf(

          )),
    HARPIE_BUG_SWARM(level = 33,
                     npcs = mapOf(

                     )),
    WALL_BEAST(level = 35,
               npcs = mapOf(

               )),
    KILLERWATT(level = 37,
               npcs = mapOf(

               )),
    BASILISK(level = 40,
             npcs = mapOf(

             ),
             unlocked = { combatLevel >= 40 && defence.staticLevel >= 20 }),
    FEVER_SPIDER(level = 42,
                 npcs = mapOf(

                 )),
    INFERNAL_MAGE(level = 45,
                  npcs = mapOf(

                  )),
    BLOODVELD(level = 50,
              npcs = mapOf(

              ),
              unlocked = { combatLevel >= 50 }),
    JELLY(level = 52,
          npcs = mapOf(

          ),
          unlocked = { combatLevel >= 57 }),
    TUROTH(level = 55,
           npcs = mapOf(

           ),
           unlocked = { combatLevel >= 60 }),
    ZYGOMITE(level = 57,
             npcs = mapOf(

             ),
             unlocked = { combatLevel >= 60 }),
    ABERRANT_SPECTRE(level = 60,
                     npcs = mapOf(

                     )),
    DUST_DEVIL(level = 65,
               npcs = mapOf(

               ),
               unlocked = { combatLevel >= 70 }),
    KURASK(level = 70,
           npcs = mapOf(

           )),
    SKELETAL_WYVERN(level = 72,
                    npcs = mapOf(

                    ),
                    unlocked = { combatLevel >= 70 }),
    GARGOYLE(level = 75,
             npcs = mapOf(

             )),
    NECHRYAEL(level = 80,
              npcs = mapOf(

              )),
    ABYSSAL_DEMON(level = 85,
                  npcs = mapOf(

                  )),
    DARK_BEAST(level = 90,
               npcs = mapOf(

               ));

    companion object {
        val VALUES = ImmutableSet.copyOf(values())

        /**
         * Mappings of slayer task NPC ids to a pair containing the task instance and XP.
         */
        val TASKS: Map<Int, Pair<SlayerTask, Double>> = run {
            val map = mutableMapOf<Int, Pair<SlayerTask, Double>>()
            for(task in VALUES) {
                for (entry in task.npcs.entries) {
                    map[entry.key] = Pair(task, entry.value)
                }
            }
            map
        }
    }
}