package world.player.skill.firemaking

/**
 * An enum representing logs that can be burned.
 */
enum class Log(val id: Int, val level: Int, val exp: Double) {
    NORMAL(id = 1511,
           level = 1,
           exp = 40.0),
    OAK(id = 1521,
        level = 15,
        exp = 60.0),
    WILLOW(id = 1519,
           level = 30,
           exp = 90.0),
    TEAK(id = 6333,
         level = 35,
         exp = 105.0),
    MAPLE(id = 1517,
          level = 45,
          exp = 135.0),
    MAHOGANY(id = 6332,
             level = 50,
             exp = 157.5),
    YEW(id = 1515,
        level = 60,
        exp = 202.5),
    MAGIC(id = 1513,
          level = 75,
          exp = 303.8);

    companion object {

        /**
         * Log item ID -> Log instance
         */
        val ID_TO_LOG = values().associateBy { it.id }
    }
}
