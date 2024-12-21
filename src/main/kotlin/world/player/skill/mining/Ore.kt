package world.player.skill.mining;

import api.predef.*

/**
 * An enumerated type representing all ores that can be mined with [Pickaxe]s.
 */
enum class Ore(val rocks: List<Pair<Int, Int>>,
               val level: Int,
               val item: Int,
               val respawnTicks: Int?,
               val resistance: Int,
               val exp: Double,
               val depletionChance: Int? = 8) {
    RUNE_ESSENCE(rocks = listOf(2491 to -1),
            level = 1,
            item = 1436,
            respawnTicks = null, // Will never respawn.
            resistance = 1,
            exp = 5.0,
            depletionChance = null), // Will never deplete.
    CLAY(rocks = listOf(2108 to 450,
            2109 to 451),
            level = 1,
            item = 434,
            respawnTicks = 2,
            resistance = 1,
            exp = 5.0,
            depletionChance = 1),
    TIN(rocks = listOf(2094 to 450,
            2095 to 451),
            level = 1,
            item = 438,
            respawnTicks = 3,
            resistance = 1,
            exp = 17.5,
            depletionChance = 1),
    COPPER(rocks = listOf(2090 to 450,
            2091 to 451),
            level = 1,
            item = 436,
            respawnTicks = 3,
            resistance = 1,
            exp = 17.5,
            depletionChance = 1),
    IRON(rocks = listOf(2092 to 450,
            2093 to 451),
            level = 1,
            item = 440,
            respawnTicks = 9,
            resistance = 3,
            exp = 17.5),
    SILVER(rocks = listOf(2100 to 450,
            2101 to 451),
            level = 20,
            item = 443,
            respawnTicks = 100,
            resistance = 4,
            exp = 17.5),
    COAL(rocks = listOf(2096 to 450,
            2097 to 451),
            level = 30,
            item = 453,
            respawnTicks = 50,
            resistance = 5,
            exp = 17.5),
    GOLD(rocks = listOf(2098 to 450,
            2099 to 451),
            level = 40,
            item = 444,
            respawnTicks = 100,
            resistance = 6,
            exp = 17.5),
    MITHRIL(rocks = listOf(2102 to 450,
            2103 to 451),
            level = 55,
            item = 447,
            respawnTicks = 200,
            resistance = 7,
            exp = 17.5),
    ADAMANT(rocks = listOf(2104 to 450,
            2105 to 451),
            level = 70,
            item = 449,
            respawnTicks = 400,
            resistance = 8,
            exp = 95.0,
            depletionChance = 10),
    RUNE(rocks = listOf(2106 to 450,
            2107 to 451),
            level = 85,
            item = 451,
            respawnTicks = 1200,
            resistance = 9,
            exp = 125.0,
            depletionChance = 12);

    val typeName = itemName(item).replace("ore", "").trim()

    companion object {

        /**
         * Ore rock object ID -> Ore instance.
         */
        val ROCK_MAP = values().flatMap { ore -> ore.rocks.map { it.first to ore} }.toMap()

        /**
         * All empty rock object IDs.
         */
        val EMPTY_ROCKS: Set<Int> =  values().flatMap { ore -> ore.rocks.map { it.second } }.toHashSet()

        /**
         * Ore rock object ID -> Empty rock object ID.
         */
        val ORE_TO_EMPTY: Map<Int, Int> =  values().flatMap {  ore -> ore.rocks.map { it.first to it.second } }.toMap()
    }
}