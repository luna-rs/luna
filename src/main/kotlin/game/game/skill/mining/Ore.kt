package game.skill.mining;

import api.predef.*
import com.google.common.collect.ImmutableSetMultimap

/**
 * An enumerated type representing all ores that can be mined with [Pickaxe]s.
 *
 * @author lare96
 */
enum class Ore(val rocks: List<Pair<Int, Int>>,
               val level: Int,
               val item: Int,
               val respawnTicks: Int?,
               val exp: Double,
               val chance: Pair<Int, Int>) {
    RUNE_ESSENCE(rocks = listOf(2491 to -1),
                 level = 1,
                 item = 1436,
                 respawnTicks = null,
                 exp = 5.0,
                 chance = 1 to 1),
    PURE_ESSENCE(rocks = emptyList(),
                 level = 30,
                 item = 7936,
                 respawnTicks = null,
                 exp = 5.0,
                 chance = 1 to 1),
    CLAY(rocks = listOf(2108 to 450,
                        2109 to 451,
                        9711 to 9723,
                        9712 to 9724,
                        9713 to 9725,
                        10949 to 10945,
                        11189 to 9736,
                        11190 to 9737,
                        11191 to 11165,
                        14904 to 14896,
                        14905 to 14897,
                        12941 to 4994,
                        12942 to 4995,
                        12943 to 4996),
         level = 1,
         item = 434,
         respawnTicks = 2,
         exp = 5.0,
         chance = 128 to 400),
    TIN(rocks = listOf(2094 to 450,
                       2095 to 451,
                       3043 to 3431,
                       9714 to 9723,
                       9715 to 9724,
                       9716 to 9725,
                       11933 to 11552,
                       11934 to 11553,
                       11935 to 11554,
                       11957 to 11555,
                       11958 to 11556,
                       11959 to 11557,
                       14902 to 14894,
                       14903 to 14895,
                       4979 to 4994,
                       4980 to 4995,
                       4981 to 4996),
        level = 1,
        item = 438,
        respawnTicks = 3,
        exp = 17.5,
        chance = 100 to 400), // Just a guess, rates haven't been released.
    COPPER(rocks = listOf(2090 to 450,
                          2091 to 451,
                          3042 to 3431,
                          9708 to 9723,
                          9709 to 9724,
                          9710 to 9725,
                          11960 to 11555,
                          11961 to 11556,
                          11962 to 11557,
                          14906 to 14898,
                          14907 to 14899,
                          4976 to 4994,
                          4977 to 4995,
                          4978 to 4996),
           level = 1,
           item = 436,
           respawnTicks = 3,
           exp = 17.5,
           chance = 100 to 400), // Just a guess, rates haven't been released.
    IRON(rocks = listOf(2092 to 450,
                        2093 to 451,
                        6943 to 6947,
                        6944 to 6948,
                        9717 to 9723,
                        9718 to 9724,
                        9719 to 9725,
                        11954 to 11555,
                        11955 to 11556,
                        11956 to 11557,
                        14856 to 14832,
                        14857 to 14833,
                        14858 to 14834,
                        14913 to 14915,
                        14914 to 14916,
                        4982 to 4994,
                        4983 to 4995,
                        4984 to 4996),
         level = 15,
         item = 440,
         respawnTicks = 9,
         exp = 35.0,
         chance = 96 to 350),
    SILVER(rocks = listOf(2100 to 450,
                          2101 to 451,
                          6945 to 6947,
                          6946 to 6948,
                          11186 to 9736,
                          11187 to 9737,
                          11188 to 11165,
                          11948 to 11555,
                          11949 to 11556,
                          11950 to 11557),
           level = 20,
           item = 442,
           respawnTicks = 100,
           exp = 40.0,
           chance = 25 to 200),
    COAL(rocks = listOf(2096 to 450,
                        2097 to 451,
                        4676 to 450,
                        10948 to 10944,
                        11930 to 11552,
                        11931 to 11553,
                        11932 to 11554,
                        11963 to 11555,
                        11964 to 11556,
                        14850 to 14832,
                        14851 to 14833,
                        14852 to 14834,
                        4985 to 4994,
                        4986 to 4995,
                        4987 to 4996),
         level = 30,
         item = 453,
         respawnTicks = 50,
         exp = 50.0,
         chance = 16 to 100),
    GOLD(rocks = listOf(2098 to 450,
                        2099 to 451,
                        9720 to 9723,
                        9721 to 9724,
                        9722 to 9725,
                        11183 to 9736,
                        11184 to 9737,
                        11185 to 11165,
                        11951 to 11555,
                        11952 to 11556,
                        11953 to 11557,
                        5989 to 5992,
                        5990 to 5992,
                        5991 to 5992),
         level = 40,
         item = 444,
         respawnTicks = 100,
         exp = 65.0,
         chance = 7 to 75),
    MITHRIL(rocks = listOf(2102 to 450,
                           2103 to 451,
                           11946 to 11556,
                           11947 to 11557,
                           14853 to 14832,
                           14854 to 14833,
                           14855 to 14834,
                           4988 to 4994,
                           4989 to 4995,
                           4990 to 4996),
            level = 55,
            item = 447,
            respawnTicks = 200,
            exp = 80.0,
            chance = 4 to 50),
    ADAMANT(rocks = listOf(2104 to 450,
                           2105 to 451,
                           14862 to 14832,
                           14863 to 14833,
                           14864 to 14834,
                           4991 to 4994,
                           4992 to 4995,
                           4993 to 4996),
            level = 70,
            item = 449,
            respawnTicks = 400,
            exp = 95.0,
            chance = 2 to 25),
    RUNE(rocks = listOf(2106 to 450,
                        2107 to 451,
                        14859 to 14832,
                        14860 to 14833,
                        14861 to 14834),
         level = 85,
         item = 451,
         respawnTicks = 1200,
         exp = 125.0,
         chance = 10 to 18);

    /**
     * The name of the type of ore.
     */
    val typeName = itemName(item).replace("ore", "").replace("Rune essence", "essence").trim()

    companion object {

        /**
         * Ore instance -> Ore rock object ID.
         */
        val ORE_MAP: ImmutableSetMultimap<Ore, Int>


        /**
         * Ore rock object ID -> Ore instance.
         */
        val ROCK_MAP = values().flatMap { ore -> ore.rocks.map { it.first to ore } }.toMap()
        val ITEM_MAP = values().associateBy { it.item }

        /**
         * All empty rock object IDs, with the empty rocks 452 and 453 that are only ever placed as scenery.
         */
        val EMPTY_ROCKS: Set<Int> = (values().flatMap { ore -> ore.rocks.map { it.second } } + listOf(452, 453)).toHashSet()

        /**
         * Ore rock object ID -> Empty rock object ID.
         */
        val ORE_TO_EMPTY: Map<Int, Int> = values().flatMap { ore -> ore.rocks.map { it.first to it.second } }.toMap()

        init {
            values().flatMap { ore -> ore.rocks.map { ore to it.first } }.apply {
                val map = ImmutableSetMultimap.builder<Ore, Int>()
                for ((ore, rock) in this) {
                    map.put(ore, rock)
                }
                ORE_MAP = map.build()
            }
        }
    }
}