package world.player.skill.woodcutting.cutTree

/**
 * An enumerated type representing all trees that can be cut down.
 *
 * @author lare96
 */
enum class Tree(val level: Int, val exp: Double, val logId: Int, val respawnTicks: Int, val maxHealth: IntRange) {
    NORMAL(level = 1,
           exp = 40.0,
           logId = 1511,
           respawnTicks = 60,
           maxHealth = 1..1),
    OAK(level = 15,
        exp = 60.0,
        logId = 1521,
        respawnTicks = 13,
        maxHealth = 2..3),
    WILLOW(level = 30,
           exp = 90.0,
           logId = 1519,
           respawnTicks = 13,
           maxHealth = 4..6),
    TEAK(level = 35,
         exp = 105.0,
         logId = 6333,
         respawnTicks = 45,
         maxHealth = 6..8),
    MAPLE(level = 45,
          exp = 135.0,
          logId = 1517,
          respawnTicks = 58,
          maxHealth = 5..8),
    MAHOGANY(level = 50,
             exp = 157.5,
             logId = 6332,
             respawnTicks = 71,
             maxHealth = 8..10),
    YEW(level = 60,
        exp = 202.5,
        logId = 1515,
        respawnTicks = 100,
        maxHealth = 10..15),
    MAGIC(level = 75,
          exp = 303.8,
          logId = 1513,
          respawnTicks = 200,
          maxHealth = 12..25);

    companion object {

        /**
         * Tree item ID -> Tree instance.
         */
        val VALUES = values().associateBy { it.logId }
    }
}