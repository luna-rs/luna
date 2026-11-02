package game.skill.woodcutting.cutTree

/**
 * An enumerated type representing all trees that can be cut down.
 *
 * [respawnTicks] contains Lost City's base durations before the online population adjustment.
 *
 * @author lare96
 */
enum class Tree(val level: Int, val exp: Double, val logId: Int, val respawnTicks: IntRange) {
    NORMAL(level = 1,
           exp = 25.0,
           logId = 1511,
           respawnTicks = 120..199),
    ACHEY(level = 1,
          exp = 25.0,
          logId = 2862,
          respawnTicks = 120..199),
    OAK(level = 15,
        exp = 37.5,
        logId = 1521,
        respawnTicks = 30..30),
    WILLOW(level = 30,
           exp = 67.5,
           logId = 1519,
           respawnTicks = 30..30),
    TEAK(level = 35,
         exp = 85.0,
         logId = 6333,
         respawnTicks = 30..30),
    MAPLE(level = 45,
          exp = 100.0,
          logId = 1517,
          respawnTicks = 120..120),
    HOLLOW(level = 45,
           exp = 82.5,
           logId = 3239,
           respawnTicks = 86..86),
    MAHOGANY(level = 50,
             exp = 125.0,
             logId = 6332,
             respawnTicks = 28..28),
    YEW(level = 60,
        exp = 175.0,
        logId = 1515,
        respawnTicks = 200..200),
    MAGIC(level = 75,
          exp = 250.0,
          logId = 1513,
          respawnTicks = 400..400);

    companion object {

        /**
         * Tree item ID -> Tree instance.
         */
        val ALL = values().associateBy { it.logId }
    }
}
