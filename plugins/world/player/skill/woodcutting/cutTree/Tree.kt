package world.player.skill.woodcutting.cutTree

/**
 * An enumerated type representing all trees that can be cut down.
 */
enum class Tree(val level: Int, val exp: Double, val logsId: Int, val respawnTicks: Int, val resistance: Int, val depletionChance: Int = 8) {
    NORMAL(level = 1,
           exp = 40.0,
           logsId = 1511,
           respawnTicks = 60,
           depletionChance = 1,
           resistance = 1),
    OAK(level = 15,
        exp = 60.0,
        logsId = 1521,
        respawnTicks = 13,
        resistance = 3),
    WILLOW(level = 30,
           exp = 90.0,
           logsId = 1519,
           respawnTicks =13,
           resistance = 5),
    TEAK(level = 35,
         exp = 105.0,
         logsId = 6333,
         respawnTicks = 45,
         resistance = 6),
    MAPLE(level = 45,
          exp = 135.0,
          logsId = 1517,
          respawnTicks = 58,
          resistance = 7),
    MAHOGANY(level = 50,
             exp = 157.5,
             logsId = 6332,
             respawnTicks = 71,
             resistance = 8),
    YEW(level = 60,
        exp = 202.5,
        logsId = 1515,
        respawnTicks = 100,
        resistance = 9),
    MAGIC(level = 75,
          exp = 303.8,
          logsId = 1513,
          respawnTicks = 200,
          resistance = 11);

    companion object {

        /**
         * Tree item ID -> Tree instance.
         */
        val VALUES = values().associateBy { it.logsId }
    }
}